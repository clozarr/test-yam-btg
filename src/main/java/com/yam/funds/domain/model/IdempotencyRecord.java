package com.yam.funds.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Reservation that makes a money-moving operation run exactly once.
 *
 * <p>The record is inserted <em>before</em> the operation runs, so a unique-key
 * violation — not a read-then-write check — is what serialises concurrent duplicates.
 * It is promoted to {@link IdempotencyStatus#COMPLETED} inside the same transaction
 * that performs the operation, which is what closes the window where money could have
 * moved while the key still said {@code IN_PROGRESS}.
 *
 * <p>Two clocks guard against a crashed instance: {@code leaseExpiresAt} marks a
 * reservation as abandoned so it can be reclaimed, and {@code expiresAt} lets storage
 * reap the record once it is no longer replayable.
 */
public record IdempotencyRecord(
        String key,
        ClientId clientId,
        IdempotencyOperation operation,
        String requestFingerprint,
        IdempotencyStatus status,
        String responsePayload,
        Instant createdAt,
        Instant leaseExpiresAt,
        Instant expiresAt) {

    public IdempotencyRecord {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }

    /**
     * Builds the storage key. Scoping by client and operation keeps a key supplied by
     * one client from ever colliding with another's.
     */
    public static String scopedKey(
            final ClientId clientId, final IdempotencyOperation operation, final String clientKey) {
        return "%s:%s:%s".formatted(clientId.value(), operation.name(), clientKey);
    }

    public static IdempotencyRecord reserve(
            final ClientId clientId,
            final IdempotencyOperation operation,
            final String clientKey,
            final String requestFingerprint,
            final Instant now,
            final Duration lease,
            final Duration retention) {
        return new IdempotencyRecord(
                scopedKey(clientId, operation, clientKey),
                clientId,
                operation,
                requestFingerprint,
                IdempotencyStatus.IN_PROGRESS,
                null,
                now,
                now.plus(lease),
                now.plus(retention));
    }

    public IdempotencyRecord complete(final String payload, final Instant now) {
        return new IdempotencyRecord(
                key,
                clientId,
                operation,
                requestFingerprint,
                IdempotencyStatus.COMPLETED,
                payload,
                createdAt,
                now,
                expiresAt);
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return status == IdempotencyStatus.IN_PROGRESS;
    }

    /** Whether a retry carries the same request body as the original attempt. */
    public boolean hasSameRequestAs(final String fingerprint) {
        return Objects.equals(requestFingerprint, fingerprint);
    }

    /** A reservation past its lease is assumed to belong to a crashed instance. */
    public boolean isLeaseExpired(final Instant now) {
        return leaseExpiresAt != null && now.isAfter(leaseExpiresAt);
    }
}
