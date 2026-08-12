package com.yam.funds.domain.model;

/**
 * Everything a single subscription or cancellation produced.
 *
 * <p>Returned by the {@link Client} aggregate so callers get the new client state and
 * the ledger entry in one step, without having to re-read the aggregate or rebuild the
 * transaction outside the domain.
 */
public record SubscriptionOutcome(Client client, Subscription subscription, FundTransaction transaction) {
}
