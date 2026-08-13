package com.yam.funds.infrastructure.in.web;

import com.yam.funds.domain.model.TransactionCursor;
import com.yam.funds.domain.model.TransactionId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Encodes the paging cursor as an opaque token.
 *
 * <p>Base64 is encoding, not protection — the point is to signal that the token is not
 * a stable API and stop callers from hand-crafting one against the internal paging key,
 * which would freeze the ledger's index layout into the public contract.
 */
public final class TransactionCursorCodec {

    private static final String SEPARATOR = "|";

    private TransactionCursorCodec() {
    }

    public static String encode(final TransactionCursor cursor) {
        if (cursor == null) {
            return null;
        }
        final String raw = cursor.occurredAt().toString() + SEPARATOR + cursor.transactionId().value();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws InvalidCursorException if the token was not produced by {@link #encode}
     */
    public static TransactionCursor decode(final String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            final String raw = new String(
                    Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            // Split on the FIRST separator: the timestamp prefix can never contain one,
            // so everything after it belongs to the identifier, separators included.
            final int separator = raw.indexOf(SEPARATOR);
            if (separator < 0) {
                throw new InvalidCursorException(token);
            }
            return new TransactionCursor(
                    Instant.parse(raw.substring(0, separator)),
                    TransactionId.of(raw.substring(separator + 1)));
        } catch (final IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCursorException(token);
        }
    }

    /** Raised when a caller supplies a cursor this service did not issue. */
    public static class InvalidCursorException extends RuntimeException {

        public InvalidCursorException(final String token) {
            super("Cursor %s is not a valid pagination token".formatted(token));
        }
    }
}
