package com.yam.funds.domain.exception;

/**
 * Raised when a client tries to subscribe to a fund without enough available balance.
 *
 * <p>The message is fixed by the business specification and is surfaced verbatim to
 * the client, which is why it is the one literal in this codebase that is not in
 * English.
 */
public class InsufficientBalanceException extends DomainException {

    public static final String ERROR_CODE = "INSUFFICIENT_BALANCE";

    private static final String MESSAGE_TEMPLATE =
            "No tiene saldo disponible para vincularse al fondo %s";

    public InsufficientBalanceException(final String fundName) {
        super(ERROR_CODE, MESSAGE_TEMPLATE.formatted(fundName));
    }
}
