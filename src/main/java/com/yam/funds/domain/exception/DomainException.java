package com.yam.funds.domain.exception;

/**
 * Base type for every business rule violation raised by the domain.
 *
 * <p>Unchecked on purpose: these travel through reactive chains, where checked
 * exceptions cannot be declared, and are translated into HTTP responses by the web
 * layer's global exception handler. The {@link #errorCode()} gives that handler a
 * stable, transport-independent discriminator.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(final String errorCode, final String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
