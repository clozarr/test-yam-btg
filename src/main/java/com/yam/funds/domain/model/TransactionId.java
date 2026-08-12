package com.yam.funds.domain.model;

import java.util.UUID;

/**
 * Identity of a {@link FundTransaction}.
 *
 * <p>Satisfies the business requirement that every transaction carries a unique
 * identifier.
 */
public record TransactionId(String value) {

    public TransactionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("transactionId must not be blank");
        }
    }

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID().toString());
    }

    public static TransactionId of(final String value) {
        return new TransactionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
