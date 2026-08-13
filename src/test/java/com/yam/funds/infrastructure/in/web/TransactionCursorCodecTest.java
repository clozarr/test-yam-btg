package com.yam.funds.infrastructure.in.web;

import com.yam.funds.domain.model.TransactionCursor;
import com.yam.funds.domain.model.TransactionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionCursorCodecTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");

    @Test
    @DisplayName("round-trips a cursor")
    void roundTrips() {
        final TransactionCursor original = new TransactionCursor(NOW, TransactionId.of("txn-1"));

        final TransactionCursor restored =
                TransactionCursorCodec.decode(TransactionCursorCodec.encode(original));

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("round-trips an identifier containing the separator")
    void roundTripsSeparatorInId() {
        // The token is split on the first separator, so the identifier keeps any it holds.
        final TransactionCursor original = new TransactionCursor(NOW, TransactionId.of("txn|weird"));

        assertThat(TransactionCursorCodec.decode(TransactionCursorCodec.encode(original)))
                .isEqualTo(original);
    }

    @Test
    @DisplayName("produces a URL-safe token with no padding")
    void producesUrlSafeToken() {
        final String token = TransactionCursorCodec.encode(
                new TransactionCursor(NOW, TransactionId.of("txn-1")));

        assertThat(token).doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("treats a missing cursor as the first page")
    void treatsMissingCursorAsFirstPage() {
        assertThat(TransactionCursorCodec.decode(null)).isNull();
        assertThat(TransactionCursorCodec.decode("  ")).isNull();
        assertThat(TransactionCursorCodec.encode(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-base64!!", "bm90LWEtY3Vyc29y", "MjAyNi0wOC0xMnxiYWQ"})
    @DisplayName("rejects a token this service did not issue")
    void rejectsForeignToken(final String token) {
        assertThatThrownBy(() -> TransactionCursorCodec.decode(token))
                .isInstanceOf(TransactionCursorCodec.InvalidCursorException.class);
    }
}
