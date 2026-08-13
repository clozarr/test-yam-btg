package com.yam.funds.infrastructure.out.kafka;

import com.yam.funds.config.FundsProperties;
import com.yam.funds.infrastructure.out.persistence.mongo.document.OutboxEventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * Forwards recorded events from the outbox to the broker.
 *
 * <p>This is the half of the transactional outbox pattern that runs outside the
 * transaction. Delivery is at-least-once by design: an event may be sent and then fail
 * to be marked as published, in which case it is sent again. Consumers must therefore be
 * idempotent — the alternative, marking it published before sending, would lose events
 * instead, which is strictly worse for a notification about someone's money.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED_AT = "createdAt";

    private final ReactiveMongoTemplate mongoTemplate;
    private final SubscriptionEventProducer producer;
    private final FundsProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.relay.interval}")
    public void forwardPendingEvents() {
        claimPending()
                // concatMap, not flatMap: events for one client must reach the broker in
                // the order they were recorded, and concurrent sends would not preserve it.
                .concatMap(this::forward)
                .doOnError(error -> log.error("[forwardPendingEvents] [END EX] Details: {}",
                        error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    /**
     * Takes ownership of each pending event with a conditional update, so two instances
     * polling at the same time do not both forward it.
     */
    private Flux<OutboxEventDocument> claimPending() {
        final Query query = Query.query(
                        Criteria.where(FIELD_STATUS).is(OutboxEventDocument.STATUS_PENDING))
                .with(Sort.by(Sort.Direction.ASC, FIELD_CREATED_AT))
                .limit(properties.outbox().relay().batchSize());

        return mongoTemplate.find(query, OutboxEventDocument.class)
                .concatMap(this::claim);
    }

    private Mono<OutboxEventDocument> claim(final OutboxEventDocument event) {
        final Query query = Query.query(Criteria.where("_id").is(event.getId())
                .and(FIELD_STATUS).is(OutboxEventDocument.STATUS_PENDING));

        return mongoTemplate.findAndModify(
                query,
                new Update().set(FIELD_STATUS, OutboxEventDocument.STATUS_PUBLISHING)
                        .inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                OutboxEventDocument.class);
    }

    private Mono<Void> forward(final OutboxEventDocument event) {
        return producer.publish(event.getPartitionKey(), event.getPayload())
                // Deferred: then() evaluates its argument at assembly time, which would
                // build the "mark published" update even on the path where the send fails.
                .then(Mono.defer(() -> markPublished(event)))
                // Releasing the claim on failure is what lets the next cycle retry it,
                // rather than leaving it stuck as PUBLISHING forever.
                .onErrorResume(error -> {
                    log.warn("[forward] event {} could not be published, releasing it for retry. "
                            + "Details: {}", event.getId(), error.getMessage());
                    return release(event);
                });
    }

    private Mono<Void> markPublished(final OutboxEventDocument event) {
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(event.getId())),
                        new Update().set(FIELD_STATUS, OutboxEventDocument.STATUS_PUBLISHED)
                                .set("publishedAt", clock.instant()),
                        OutboxEventDocument.class)
                .then();
    }

    private Mono<Void> release(final OutboxEventDocument event) {
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(event.getId())),
                        new Update().set(FIELD_STATUS, OutboxEventDocument.STATUS_PENDING),
                        OutboxEventDocument.class)
                .then();
    }
}
