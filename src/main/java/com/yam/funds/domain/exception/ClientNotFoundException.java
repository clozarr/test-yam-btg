package com.yam.funds.domain.exception;

import com.yam.funds.domain.model.ClientId;

/** Raised when the requested client does not exist. */
public class ClientNotFoundException extends DomainException {

    public static final String ERROR_CODE = "CLIENT_NOT_FOUND";

    public ClientNotFoundException(final ClientId clientId) {
        super(ERROR_CODE, "Client %s does not exist".formatted(clientId));
    }
}
