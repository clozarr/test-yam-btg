package com.yam.funds.domain.port.out;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.TransactionCursor;
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
     * Reads a page of a client's entries, newest first.
     *
     * @param clientId client whose ledger is read
     * @param cursor   position to continue from; {@code null} starts at the newest entry
     * @param limit    maximum entries to return
     * @return the matching entries in descending chronological order
     */
    Flux<FundTransaction> findByClient(ClientId clientId, TransactionCursor cursor, int limit);
}
