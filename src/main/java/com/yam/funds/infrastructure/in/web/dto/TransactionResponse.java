package com.yam.funds.infrastructure.in.web.dto;

import com.yam.funds.domain.model.FundTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A subscription opening or cancellation recorded in the ledger")
public record TransactionResponse(
        @Schema(example = "3f1c8a2e-7b64-4c1f-9a2e-8d5b1c3f7a90") String transactionId,
        String subscriptionId,
        String fundId,
        String fundName,
        @Schema(example = "OPENING", allowableValues = {"OPENING", "CANCELLATION"}) String type,
        MoneyResponse amount,
        MoneyResponse balanceAfter,
        Instant occurredAt) {

    public static TransactionResponse from(final FundTransaction transaction) {
        return new TransactionResponse(
                transaction.id().value(),
                transaction.subscriptionId() == null ? null : transaction.subscriptionId().value(),
                transaction.fundId().value(),
                transaction.fundName(),
                transaction.type().name(),
                MoneyResponse.from(transaction.amount()),
                MoneyResponse.from(transaction.balanceAfter()),
                transaction.occurredAt());
    }
}
