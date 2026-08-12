package com.yam.funds.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyRecordTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final ClientId CLIENT = ClientId.of("client-1");

    private static IdempotencyRecord reserved() {
        return IdempotencyRecord.reserve(
                CLIENT, IdempotencyOperation.SUBSCRIBE, "key-1", "fingerprint-a", NOW, LEASE, RETENTION);
    }

    @Test
    @DisplayName("scopes the key by client and operation so keys cannot collide")
    void scopesKey() {
        final String subscribeKey =
                IdempotencyRecord.scopedKey(CLIENT, IdempotencyOperation.SUBSCRIBE, "key-1");
        final String cancelKey =
                IdempotencyRecord.scopedKey(CLIENT, IdempotencyOperation.CANCEL, "key-1");
        final String otherClientKey =
                IdempotencyRecord.scopedKey(ClientId.of("client-2"), IdempotencyOperation.SUBSCRIBE, "key-1");

        assertThat(subscribeKey).isNotEqualTo(cancelKey).isNotEqualTo(otherClientKey);
    }

    @Test
    @DisplayName("is reserved in progress with both expiry clocks set")
    void reservesInProgress() {
        final IdempotencyRecord record = reserved();

        assertThat(record.isInProgress()).isTrue();
        assertThat(record.isCompleted()).isFalse();
        assertThat(record.responsePayload()).isNull();
        assertThat(record.leaseExpiresAt()).isEqualTo(NOW.plus(LEASE));
        assertThat(record.expiresAt()).isEqualTo(NOW.plus(RETENTION));
    }

    @Test
    @DisplayName("keeps the response payload once completed")
    void completesWithPayload() {
        final IdempotencyRecord completed = reserved().complete("{\"transactionId\":\"txn-1\"}", NOW);

        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.responsePayload()).isEqualTo("{\"transactionId\":\"txn-1\"}");
        assertThat(completed.expiresAt()).isEqualTo(NOW.plus(RETENTION));
    }

    @Test
    @DisplayName("detects a retry that changed the request body")
    void detectsFingerprintMismatch() {
        final IdempotencyRecord record = reserved();

        assertThat(record.hasSameRequestAs("fingerprint-a")).isTrue();
        assertThat(record.hasSameRequestAs("fingerprint-b")).isFalse();
    }

    @Test
    @DisplayName("treats a reservation past its lease as abandoned")
    void detectsExpiredLease() {
        final IdempotencyRecord record = reserved();

        assertThat(record.isLeaseExpired(NOW.plusSeconds(10))).isFalse();
        assertThat(record.isLeaseExpired(NOW.plusSeconds(31))).isTrue();
    }
}
