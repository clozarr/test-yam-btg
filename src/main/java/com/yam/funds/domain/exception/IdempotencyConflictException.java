package com.yam.funds.domain.exception;

/**
 * Raised when an idempotency key is reused with a different request body.
 *
 * <p>Surfaced rather than swallowed: silently replaying the first response for a
 * request that asked for something else would hide a genuine bug in the caller.
 */
public class IdempotencyConflictException extends DomainException {

    public static final String ERROR_CODE = "IDEMPOTENCY_KEY_REUSED";

    public IdempotencyConflictException(final String idempotencyKey) {
        super(ERROR_CODE, "Idempotency key %s was already used with a different request"
                .formatted(idempotencyKey));
    }
}
