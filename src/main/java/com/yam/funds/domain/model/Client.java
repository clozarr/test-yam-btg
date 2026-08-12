package com.yam.funds.domain.model;

import com.yam.funds.domain.exception.AlreadySubscribedException;
import com.yam.funds.domain.exception.FundNotAvailableException;
import com.yam.funds.domain.exception.InsufficientBalanceException;
import com.yam.funds.domain.exception.MinimumAmountNotMetException;
import com.yam.funds.domain.exception.SubscriptionNotFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root owning a client's balance and their active fund subscriptions.
 *
 * <p>Balance and subscriptions live in the same aggregate because the rule "a client
 * cannot subscribe without sufficient balance" couples them: they must change together
 * or not at all. Persisted as a single document, that invariant is protected by
 * document-level atomicity without needing a multi-document transaction.
 *
 * <p>Only <em>active</em> subscriptions are held here, keyed by fund. That keeps the
 * aggregate bounded by the size of the catalogue and makes "one active subscription per
 * fund" a structural guarantee rather than a check. Cancelled subscriptions are not
 * forgotten — they survive as {@link FundTransaction} ledger entries.
 *
 * <p>Identifiers and timestamps are passed in rather than generated internally, so the
 * aggregate stays deterministic and testable.
 */
public record Client(
        ClientId id,
        String fullName,
        String email,
        String phone,
        NotificationChannel notificationPreference,
        Money balance,
        Map<FundId, Subscription> activeSubscriptions,
        Long version) {

    public Client {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(notificationPreference, "notificationPreference must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        activeSubscriptions = activeSubscriptions == null ? Map.of() : Map.copyOf(activeSubscriptions);
    }

    /** Registers a new client with their opening balance and no subscriptions. */
    public static Client register(
            final ClientId id,
            final String fullName,
            final String email,
            final String phone,
            final NotificationChannel notificationPreference,
            final Money initialBalance) {
        return new Client(id, fullName, email, phone, notificationPreference, initialBalance, Map.of(), null);
    }

    public boolean isSubscribedTo(final FundId fundId) {
        return activeSubscriptions.containsKey(fundId);
    }

    public Optional<Subscription> findActiveSubscription(final FundId fundId) {
        return Optional.ofNullable(activeSubscriptions.get(fundId));
    }

    /**
     * Links this client to the given fund, debiting the linked amount from the balance.
     *
     * @throws FundNotAvailableException    if the fund is closed to new subscriptions
     * @throws AlreadySubscribedException   if an active subscription to the fund exists
     * @throws MinimumAmountNotMetException if the amount is below the fund's minimum
     * @throws InsufficientBalanceException if the balance does not cover the amount
     */
    public SubscriptionOutcome subscribeTo(
            final Fund fund,
            final Money amount,
            final SubscriptionId subscriptionId,
            final TransactionId transactionId,
            final Instant occurredAt) {

        if (!fund.active()) {
            throw new FundNotAvailableException(fund.name());
        }
        if (isSubscribedTo(fund.id())) {
            throw new AlreadySubscribedException(id, fund.name());
        }
        if (!fund.acceptsAmount(amount)) {
            throw new MinimumAmountNotMetException(fund.name(), fund.minimumAmount(), amount);
        }
        if (balance.isLessThan(amount)) {
            throw new InsufficientBalanceException(fund.name());
        }

        final Subscription subscription = Subscription.open(subscriptionId, fund, amount, occurredAt);
        final Money remainingBalance = balance.minus(amount);

        final Map<FundId, Subscription> updated = new LinkedHashMap<>(activeSubscriptions);
        updated.put(fund.id(), subscription);

        return new SubscriptionOutcome(
                withBalanceAndSubscriptions(remainingBalance, updated),
                subscription,
                FundTransaction.opening(transactionId, id, subscription, remainingBalance, occurredAt));
    }

    /**
     * Cancels the active subscription to the given fund, returning the linked amount
     * to the balance.
     *
     * @throws SubscriptionNotFoundException if no active subscription to the fund exists
     */
    public SubscriptionOutcome cancelSubscriptionTo(
            final FundId fundId, final TransactionId transactionId, final Instant occurredAt) {

        final Subscription active = findActiveSubscription(fundId)
                .orElseThrow(() -> new SubscriptionNotFoundException(id, fundId));

        final Subscription cancelled = active.cancel(occurredAt);
        final Money restoredBalance = balance.plus(active.linkedAmount());

        final Map<FundId, Subscription> updated = new LinkedHashMap<>(activeSubscriptions);
        updated.remove(fundId);

        return new SubscriptionOutcome(
                withBalanceAndSubscriptions(restoredBalance, updated),
                cancelled,
                FundTransaction.cancellation(transactionId, id, cancelled, restoredBalance, occurredAt));
    }

    private Client withBalanceAndSubscriptions(
            final Money newBalance, final Map<FundId, Subscription> newSubscriptions) {
        return new Client(
                id, fullName, email, phone, notificationPreference, newBalance, newSubscriptions, version);
    }
}
