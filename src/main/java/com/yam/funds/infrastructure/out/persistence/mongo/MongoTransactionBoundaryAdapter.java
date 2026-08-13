package com.yam.funds.infrastructure.out.persistence.mongo;

import com.yam.funds.domain.port.out.TransactionBoundaryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Runs an operation inside a MongoDB multi-document transaction.
 *
 * <p>Uses {@link TransactionalOperator} rather than {@code @Transactional}, which does
 * not apply to a reactive pipeline: the annotation would commit when the method
 * returned the publisher, long before any work had been subscribed to.
 *
 * <p>Requires MongoDB to run as a replica set — transactions are rejected on a
 * standalone server. docker-compose starts a single-node replica set for that reason.
 */
@Component
@RequiredArgsConstructor
public class MongoTransactionBoundaryAdapter implements TransactionBoundaryPort {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> executeInTransaction(final Mono<T> operation) {
        return operation.as(transactionalOperator::transactional);
    }
}
