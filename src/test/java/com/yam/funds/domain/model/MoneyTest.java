package com.yam.funds.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("normalises the scale so equal amounts are equal objects")
        void normalisesScale() {
            assertThat(Money.cop(new BigDecimal("75000")))
                    .isEqualTo(Money.cop(new BigDecimal("75000.00")))
                    .isEqualTo(Money.cop(75_000L));
        }

        @Test
        @DisplayName("rounds half-even to two decimals")
        void roundsHalfEven() {
            assertThat(Money.cop(new BigDecimal("1.005")).amount()).isEqualTo(new BigDecimal("1.00"));
            assertThat(Money.cop(new BigDecimal("1.015")).amount()).isEqualTo(new BigDecimal("1.02"));
        }

        @Test
        @DisplayName("rejects negative amounts")
        void rejectsNegative() {
            assertThatThrownBy(() -> Money.cop(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be negative");
        }

        @Test
        @DisplayName("rejects a null amount")
        void rejectsNullAmount() {
            assertThatThrownBy(() -> Money.cop((BigDecimal) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void addsAndSubtracts() {
            assertThat(Money.cop(500_000).minus(Money.cop(75_000))).isEqualTo(Money.cop(425_000));
            assertThat(Money.cop(425_000).plus(Money.cop(75_000))).isEqualTo(Money.cop(500_000));
        }

        @Test
        @DisplayName("refuses to produce a negative result")
        void refusesNegativeResult() {
            assertThatThrownBy(() -> Money.cop(10).minus(Money.cop(11)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses to mix currencies")
        void refusesMixedCurrencies() {
            final Money dollars = Money.of(BigDecimal.TEN, USD);
            assertThatThrownBy(() -> Money.cop(10).plus(dollars))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different currencies");
        }
    }

    @Nested
    @DisplayName("comparison")
    class Comparison {

        @ParameterizedTest(name = "{0} >= {1} is {2}")
        @CsvSource({
                "100, 100, true",
                "101, 100, true",
                "99,  100, false",
                "0,   0,   true"
        })
        void comparesAmounts(final long left, final long right, final boolean expected) {
            assertThat(Money.cop(left).isGreaterThanOrEqualTo(Money.cop(right))).isEqualTo(expected);
            assertThat(Money.cop(left).isLessThan(Money.cop(right))).isEqualTo(!expected);
        }

        @Test
        void detectsZero() {
            assertThat(Money.zero().isZero()).isTrue();
            assertThat(Money.cop(1).isZero()).isFalse();
        }
    }
}
