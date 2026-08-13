package com.yam.funds.infrastructure.out.persistence.mongo.outbox;

import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import com.yam.funds.domain.port.out.EventPublisherPort;
import com.yam.funds.infrastructure.out.persistence.mongo.document.OutboxEventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Records domain events in the outbox instead of publishing them straight to the broker.
 *
 * <p>Called inside the caller's transaction, so the event commits with the state change
 * it describes. Publishing to Kafka here would break that: a rollback after a successful
 * send would announce a subscription that never happened, and a broker outage would fail
 * a subscription that has nothing to do with messaging.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private static final String AGGREGATE_TYPE = "Client";
    private static final String EVENT_TYPE = "SubscriptionOpened";

    private final ReactiveMongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public Mono<Void> publish(final SubscriptionOpenedEvent event) {
        return Mono.fromCallable(() -> toDocument(event))
                .flatMap(mongoTemplate::insert)
                .doOnSuccess(stored -> log.debug("[publish] outbox event {} recorded for client {}",
                        stored.getId(), stored.getPartitionKey()))
                .then();
    }

    private OutboxEventDocument toDocument(final SubscriptionOpenedEvent event) {
        return OutboxEventDocument.builder()
                .id(event.eventId())
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(event.clientId().value())
                .eventType(EVENT_TYPE)
                // Keyed by client so every event for one client lands on the same
                // partition, which is the only way their order is guaranteed.
                .partitionKey(event.clientId().value())
                .payload(objectMapper.writeValueAsString(SubscriptionOpenedPayload.from(event)))
                .status(OutboxEventDocument.STATUS_PENDING)
                .createdAt(clock.instant())
                .attempts(0)
                .build();
    }
}
