package com.yam.funds.domain.exception;

/**
 * Raised when a request arrives while another one holding the same idempotency key is
 * still in flight. The caller should retry once the first attempt settles.
 */
public class OperationInProgressException extends DomainException {

    public static final String ERROR_CODE = "OPERATION_IN_PROGRESS";

    public OperationInProgressException(final String idempotencyKey) {
        super(ERROR_CODE, "A request with idempotency key %s is currently being processed"
                .formatted(idempotencyKey));
    }
}
