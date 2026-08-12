package com.yam.funds.domain.exception;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;

/** Raised when cancelling a subscription the client does not currently hold. */
public class SubscriptionNotFoundException extends DomainException {

    public static final String ERROR_CODE = "SUBSCRIPTION_NOT_FOUND";

    public SubscriptionNotFoundException(final ClientId clientId, final FundId fundId) {
        super(ERROR_CODE, "Client %s has no active subscription to fund %s".formatted(clientId, fundId));
    }
}
