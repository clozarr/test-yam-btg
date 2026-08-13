package com.yam.funds.domain.port.in.command;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;

import java.util.Objects;

/**
 * Request to cancel a client's active subscription to a fund.
 *
 * @param clientId       client cancelling the subscription
 * @param fundId         fund to unlink from
 * @param idempotencyKey caller-supplied key making the operation replay-safe; required,
 *                       because this returns money to the balance
 */
public record CancelFundSubscriptionCommand(ClientId clientId, FundId fundId, String idempotencyKey) {

    /** Rejects a command that could not be executed safely. */
    public CancelFundSubscriptionCommand {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(fundId, "fundId must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for money-moving operations");
        }
    }
}
