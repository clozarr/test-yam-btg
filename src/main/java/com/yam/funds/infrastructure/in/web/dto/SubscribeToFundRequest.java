package com.yam.funds.infrastructure.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Amount to link to a fund")
public record SubscribeToFundRequest(

        @Schema(example = "75000.00", description = "Must reach the fund's minimum subscription amount")
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "amount supports at most 2 decimal places")
        BigDecimal amount) {
}
