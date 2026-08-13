package com.yam.funds.infrastructure.in.web.dto;

import com.yam.funds.domain.model.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A monetary amount and its currency")
public record MoneyResponse(
        @Schema(example = "425000.00") BigDecimal amount,
        @Schema(example = "COP") String currency) {

    public static MoneyResponse from(final Money money) {
        return new MoneyResponse(money.amount(), money.currency().getCurrencyCode());
    }
}
