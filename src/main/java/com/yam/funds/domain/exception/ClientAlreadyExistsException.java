package com.yam.funds.domain.exception;

/** Raised when registering a client with an email address that is already taken. */
public class ClientAlreadyExistsException extends DomainException {

    public static final String ERROR_CODE = "CLIENT_ALREADY_EXISTS";

    public ClientAlreadyExistsException(final String email) {
        super(ERROR_CODE, "A client is already registered with email %s".formatted(email));
    }
}
