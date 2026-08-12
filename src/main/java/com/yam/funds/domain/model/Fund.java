package com.yam.funds.domain.model;

import java.util.Objects;

/**
 * Investment fund offered by the platform.
 *
 * <p>Aggregate root for master data. Fund definitions are read-only at runtime: they
 * are seeded from the business catalogue and never mutated by a use case.
 */
public record Fund(FundId id, String name, Money minimumAmount, FundCategory category, boolean active) {

    public Fund {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(minimumAmount, "minimumAmount must not be null");
        Objects.requireNonNull(category, "category must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("fund name must not be blank");
        }
    }

    /** Whether the given amount reaches this fund's minimum subscription amount. */
    public boolean acceptsAmount(final Money amount) {
        return amount.isGreaterThanOrEqualTo(minimumAmount);
    }
}
