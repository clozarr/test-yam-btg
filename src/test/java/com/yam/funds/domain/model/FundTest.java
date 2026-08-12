package com.yam.funds.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundTest {

    private static final Fund DEUDA_PRIVADA = new Fund(
            FundId.of("3"), "DEUDAPRIVADA", Money.cop(50_000), FundCategory.FIC, true);

    @ParameterizedTest(name = "an amount of {0} is accepted: {1}")
    @CsvSource({
            "49999, false",
            "50000, true",
            "50001, true"
    })
    @DisplayName("accepts amounts at or above the minimum")
    void acceptsAmountsAtOrAboveMinimum(final long amount, final boolean expected) {
        assertThat(DEUDA_PRIVADA.acceptsAmount(Money.cop(amount))).isEqualTo(expected);
    }

    @Test
    @DisplayName("rejects a blank name")
    void rejectsBlankName() {
        assertThatThrownBy(() -> new Fund(FundId.of("1"), " ", Money.cop(1), FundCategory.FPV, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    @DisplayName("rejects a blank identifier")
    void rejectsBlankId() {
        assertThatThrownBy(() -> FundId.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
