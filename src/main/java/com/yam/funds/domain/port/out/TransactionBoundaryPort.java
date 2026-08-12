package com.yam.funds.domain.port.out;

import reactor.core.publisher.Mono;

/**
 * Runs a sequence of writes as one atomic unit.
 *
 * <p>Exists so use cases can demand atomicity without importing a persistence
 * framework: the transaction manager stays behind this port, in infrastructure.
 *
 * <p>The subscription flow needs it because the balance debit, the ledger entry and
 * the outbox event live in different documents. The core invariant — balance versus
 * subscriptions — is already atomic inside the {@link com.yam.funds.domain.model.Client}
 * document; this covers the writes around it.
 */
public interface TransactionBoundaryPort {

    /**
     * Executes the given operation atomically.
     *
     * <p>The operation is committed when it completes and rolled back if it signals an
     * error, including a business rule violation — so a rejected subscription leaves no
     * ledger entry, no outbox event and no idempotency record behind, and the caller can
     * safely retry with the same key.
     *
     * @param operation the writes to commit together
     * @param <T>       the operation's result type
     * @return the operation's result, once committed
     */
    <T> Mono<T> executeInTransaction(Mono<T> operation);
}
