package com.yam.funds.infrastructure.out.persistence.mongo;

import com.yam.funds.infrastructure.out.persistence.mongo.document.ClientDocument;
import com.yam.funds.infrastructure.out.persistence.mongo.document.FundTransactionDocument;
import com.yam.funds.infrastructure.out.persistence.mongo.document.IdempotencyDocument;
import com.yam.funds.infrastructure.out.persistence.mongo.document.OutboxEventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Creates the indexes the persistence adapters depend on.
 *
 * <p>Declared explicitly rather than through {@code @Indexed} with automatic creation,
 * which is disabled: implicit index building on startup is a well-known way to stall a
 * production deployment against a large collection. Here each index is stated, ordered
 * and reviewable.
 *
 * <p>The unique index on the idempotency key is not an optimisation — it is the
 * mechanism that makes the exactly-once guarantee work. It comes for free as the
 * {@code _id} primary key, which is why the scoped key is stored there.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexInitializer {

    private final ReactiveMongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        clientIndexes()
                .then(transactionIndexes())
                .then(idempotencyIndexes())
                .then(outboxIndexes())
                .doOnSuccess(ignored -> log.info("[createIndexes] [END OK] MongoDB indexes are in place"))
                .doOnError(error -> log.error("[createIndexes] [END EX] Details: {}", error.getMessage()))
                .subscribe();
    }

    /** Sparse, because a client who chose SMS may have no email address at all. */
    private Mono<String> clientIndexes() {
        return indexesFor(ClientDocument.COLLECTION)
                .createIndex(new Index().on("email", Sort.Direction.ASC).unique().sparse());
    }

    /** Matches the ledger's keyset paging: same fields, same order, same direction. */
    private Mono<String> transactionIndexes() {
        return indexesFor(FundTransactionDocument.COLLECTION)
                .createIndex(new Index()
                        .on("clientId", Sort.Direction.ASC)
                        .on("occurredAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
    }

    /** TTL index, so expired reservations are reaped without a cleanup job. */
    private Mono<String> idempotencyIndexes() {
        return indexesFor(IdempotencyDocument.COLLECTION)
                .createIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO));
    }

    /** Serves the relay's "oldest pending events first" query. */
    private Mono<String> outboxIndexes() {
        return indexesFor(OutboxEventDocument.COLLECTION)
                .createIndex(new Index()
                        .on("status", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC));
    }

    private ReactiveIndexOperations indexesFor(final String collection) {
        return mongoTemplate.indexOps(collection);
    }
}
