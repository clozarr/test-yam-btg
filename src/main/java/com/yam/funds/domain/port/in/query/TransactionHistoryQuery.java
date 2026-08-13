package com.yam.funds.domain.port.in.query;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.TransactionCursor;

import java.util.Objects;
import java.util.Optional;

/**
 * Request for a page of a client's transaction history, newest first.
 *
 * @param clientId client whose ledger is being read
 * @param cursor   position to continue from; {@code null} starts at the newest entry
 * @param limit    maximum entries to return, capped by {@link #MAX_LIMIT}
 */
public record TransactionHistoryQuery(ClientId clientId, TransactionCursor cursor, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public TransactionHistoryQuery {
        Objects.requireNonNull(clientId, "clientId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        limit = Math.min(limit, MAX_LIMIT);
    }

    public static TransactionHistoryQuery firstPage(final ClientId clientId) {
        return new TransactionHistoryQuery(clientId, null, DEFAULT_LIMIT);
    }

    public Optional<TransactionCursor> findCursor() {
        return Optional.ofNullable(cursor);
    }
}
