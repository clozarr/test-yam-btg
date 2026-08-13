package com.yam.funds.domain.port.in;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.port.in.query.TransactionHistoryQuery;
import reactor.core.publisher.Flux;

/** Reads a client's history of subscription openings and cancellations. */
public interface GetTransactionHistoryUseCase {

    /**
     * Returns a page of the client's ledger, newest first.
     *
     * <p>Paging is cursor-based rather than offset-based: the ledger grows at the head,
     * so an offset would shift under a reader as new transactions arrive.
     *
     * @param query the client, the position to continue from and the page size
     * @return the matching ledger entries, at most {@code query.limit()} of them
     * @throws ClientNotFoundException if the client does not exist
     */
    Flux<FundTransaction> findTransactionHistory(TransactionHistoryQuery query);
}
