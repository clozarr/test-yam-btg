package com.yam.funds.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A client's link to a fund.
 *
 * <p>Local entity of the {@link Client} aggregate: its identity only has meaning
 * inside the owning client, and it is never loaded or persisted on its own. The fund
 * name is denormalised here so that cancelling a subscription — and reporting it —
 * never needs to reach into the fund catalogue.
 */
public record Subscription(
        SubscriptionId id,
        FundId fundId,
        String fundName,
        Money linkedAmount,
        SubscriptionStatus status,
        Instant openedAt,
        Instant cancelledAt) {

    public Subscription {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(fundId, "fundId must not be null");
        Objects.requireNonNull(linkedAmount, "linkedAmount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
    }

    public static Subscription open(
            final SubscriptionId id, final Fund fund, final Money amount, final Instant openedAt) {
        return new Subscription(
                id, fund.id(), fund.name(), amount, SubscriptionStatus.ACTIVE, openedAt, null);
    }

    /**
     * @throws IllegalStateException if the subscription is not active; the aggregate
     *                               only ever holds active subscriptions, so reaching
     *                               this is a bug rather than a business outcome.
     */
    public Subscription cancel(final Instant cancelledAt) {
        if (!isActive()) {
            throw new IllegalStateException("Subscription %s is not active".formatted(id));
        }
        return new Subscription(
                id, fundId, fundName, linkedAmount, SubscriptionStatus.CANCELLED, openedAt, cancelledAt);
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }
}
