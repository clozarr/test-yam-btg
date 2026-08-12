package com.yam.funds.domain.model;

import java.util.UUID;

/** Identity of a {@link Subscription}, a local entity inside the {@link Client} aggregate. */
public record SubscriptionId(String value) {

    public SubscriptionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("subscriptionId must not be blank");
        }
    }

    public static SubscriptionId generate() {
        return new SubscriptionId(UUID.randomUUID().toString());
    }

    public static SubscriptionId of(final String value) {
        return new SubscriptionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
