package com.yam.funds.domain.port.out;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.TransactionCursor;
import com.yam.funds.domain.model.TransactionId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Append-only store of ledger entries. */
public interface TransactionRepositoryPort {

    /**
     * Appends a ledger entry.
     *
     * <p>Entries are never updated or deleted once written — the ledger is the audit
     * trail, so correcting it means appending a compensating entry, not editing history.
     *
     * @param transaction entry to append
     * @return the stored entry
     */
    Mono<FundTransaction> save(FundTransaction transaction);

    /**
     * Reads a single entry.
     *
     * <p>Used to replay the result of an operation that already ran under a given
     * idempotency key: the stored key holds the transaction's id, so the response is
     * rebuilt from the ledger rather than from a serialised copy that could drift out
     * of step with it.
     *
     * @param transactionId entry to load
     * @return the entry, or an empty {@code Mono} if it does not exist
     */
    Mono<FundTransaction> findById(TransactionId transactionId);

    /**
     * Reads a page of a client's entries, newest first.
     *
     * @param clientId client whose ledger is read
     * @param cursor   position to continue from; {@code null} starts at the newest entry
     * @param limit    maximum entries to return
     * @return the matching entries in descending chronological order
     */
    Flux<FundTransaction> findByClient(ClientId clientId, TransactionCursor cursor, int limit);
}
