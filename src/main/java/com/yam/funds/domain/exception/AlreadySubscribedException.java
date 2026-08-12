package com.yam.funds.domain.exception;

import com.yam.funds.domain.model.ClientId;

/**
 * Raised when a client already holds an active subscription to the requested fund.
 *
 * <p>Also acts as the last line of defence against a duplicated request that slipped
 * past the idempotency check.
 */
public class AlreadySubscribedException extends DomainException {

    public static final String ERROR_CODE = "ALREADY_SUBSCRIBED";

    public AlreadySubscribedException(final ClientId clientId, final String fundName) {
        super(ERROR_CODE, "Client %s already holds an active subscription to fund %s"
                .formatted(clientId, fundName));
    }
}
