package com.yam.funds.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Position in the transaction ledger, used to page through history.
 *
 * <p>Keyed on the timestamp <em>and</em> the transaction id rather than the timestamp
 * alone: two transactions can share an instant, and an offset-based page would then
 * silently skip or repeat entries. Callers ask for the entries strictly older than
 * this position.
 */
public record TransactionCursor(Instant occurredAt, TransactionId transactionId) {

    public TransactionCursor {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
    }

    public static TransactionCursor of(final FundTransaction transaction) {
        return new TransactionCursor(transaction.occurredAt(), transaction.id());
    }
}
