package com.yam.funds.domain.port.in.result;

import com.yam.funds.domain.model.FundTransaction;

/**
 * Outcome of a money-moving operation.
 *
 * @param transaction the ledger entry that was recorded
 * @param replayed    {@code true} when the operation had already run under the same
 *                    idempotency key and this is the stored result rather than a fresh
 *                    execution; lets the web layer flag the replay instead of pretending
 *                    the work happened twice
 */
public record TransactionReceipt(FundTransaction transaction, boolean replayed) {

    public static TransactionReceipt executed(final FundTransaction transaction) {
        return new TransactionReceipt(transaction, false);
    }

    public static TransactionReceipt replayed(final FundTransaction transaction) {
        return new TransactionReceipt(transaction, true);
    }
}
