package com.yam.funds.infrastructure.in.web.dto;

import com.yam.funds.domain.port.in.result.TransactionReceipt;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a subscription or cancellation")
public record SubscriptionReceiptResponse(
        TransactionResponse transaction,

        @Schema(description = "True when this response replays an operation that had already "
                + "run under the same idempotency key, rather than a fresh execution")
        boolean replayed) {

    public static SubscriptionReceiptResponse from(final TransactionReceipt receipt) {
        return new SubscriptionReceiptResponse(
                TransactionResponse.from(receipt.transaction()), receipt.replayed());
    }
}
