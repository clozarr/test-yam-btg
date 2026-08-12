package com.yam.funds.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable ledger entry recording a subscription opening or cancellation.
 *
 * <p>Its own aggregate: append-only, unbounded in number and never needed to enforce
 * an invariant, so keeping it out of the {@link Client} document is what stops that
 * document from growing without limit.
 */
public record FundTransaction(
        TransactionId id,
        ClientId clientId,
        FundId fundId,
        String fundName,
        SubscriptionId subscriptionId,
        TransactionType type,
        Money amount,
        Money balanceAfter,
        Instant occurredAt) {

    public FundTransaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(fundId, "fundId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static FundTransaction opening(
            final TransactionId id,
            final ClientId clientId,
            final Subscription subscription,
            final Money balanceAfter,
            final Instant occurredAt) {
        return of(id, clientId, subscription, TransactionType.OPENING, balanceAfter, occurredAt);
    }

    public static FundTransaction cancellation(
            final TransactionId id,
            final ClientId clientId,
            final Subscription subscription,
            final Money balanceAfter,
            final Instant occurredAt) {
        return of(id, clientId, subscription, TransactionType.CANCELLATION, balanceAfter, occurredAt);
    }

    private static FundTransaction of(
            final TransactionId id,
            final ClientId clientId,
            final Subscription subscription,
            final TransactionType type,
            final Money balanceAfter,
            final Instant occurredAt) {
        return new FundTransaction(
                id,
                clientId,
                subscription.fundId(),
                subscription.fundName(),
                subscription.id(),
                type,
                subscription.linkedAmount(),
                balanceAfter,
                occurredAt);
    }
}
