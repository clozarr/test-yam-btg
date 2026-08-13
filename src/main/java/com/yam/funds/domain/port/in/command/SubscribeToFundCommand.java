package com.yam.funds.domain.port.in.command;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;

import java.util.Objects;

/**
 * Request to link a client to a fund.
 *
 * @param clientId       client opening the subscription
 * @param fundId         fund to subscribe to
 * @param amount         amount to link; must reach the fund's minimum
 * @param idempotencyKey caller-supplied key making the operation replay-safe; required,
 *                       because this moves money
 */
public record SubscribeToFundCommand(ClientId clientId, FundId fundId, Money amount, String idempotencyKey) {

    public SubscribeToFundCommand {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(fundId, "fundId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required for money-moving operations");
        }
    }
}
