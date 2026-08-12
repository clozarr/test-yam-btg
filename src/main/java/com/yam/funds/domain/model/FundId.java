package com.yam.funds.domain.model;

/**
 * Identity of the {@link Fund} aggregate.
 *
 * <p>Deliberately not a UUID: fund identifiers are fixed by the business catalogue
 * ("1".."5") and appear in public URLs, where a short readable code is preferable.
 */
public record FundId(String value) {

    public FundId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("fundId must not be blank");
        }
    }

    public static FundId of(final String value) {
        return new FundId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
