package com.yam.funds.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Monetary amount, always backed by {@link BigDecimal} — never a floating point type.
 *
 * <p>Amounts are normalised to a fixed scale on construction so two values representing
 * the same amount are always equal, and are constrained to be non-negative: this domain
 * has no concept of negative money. Building a negative amount is a programming error,
 * not a business outcome — callers check the balance first and raise the corresponding
 * business exception.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final Currency COP = Currency.getInstance("COP");

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("A monetary amount must not be negative: " + amount);
        }
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(final BigDecimal amount, final Currency currency) {
        return new Money(amount, currency);
    }

    public static Money cop(final BigDecimal amount) {
        return new Money(amount, COP);
    }

    public static Money cop(final long amount) {
        return new Money(BigDecimal.valueOf(amount), COP);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO, COP);
    }

    public Money plus(final Money other) {
        assertSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /**
     * @throws IllegalArgumentException if the result would be negative; check
     *                                  {@link #isGreaterThanOrEqualTo(Money)} first.
     */
    public Money minus(final Money other) {
        assertSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isGreaterThanOrEqualTo(final Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(final Money other) {
        return !isGreaterThanOrEqualTo(other);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(final Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }

    private void assertSameCurrency(final Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: %s and %s"
                            .formatted(currency.getCurrencyCode(), other.currency.getCurrencyCode()));
        }
    }
}
