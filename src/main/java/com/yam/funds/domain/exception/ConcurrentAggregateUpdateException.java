package com.yam.funds.domain.exception;

import com.yam.funds.domain.model.ClientId;

/**
 * Raised when an aggregate was modified by someone else between being read and written.
 *
 * <p>Declared in the domain so the ports stay free of any persistence framework:
 * adapters translate whatever their driver throws into this type.
 */
public class ConcurrentAggregateUpdateException extends DomainException {

    public static final String ERROR_CODE = "CONCURRENT_MODIFICATION";

    public ConcurrentAggregateUpdateException(final ClientId clientId) {
        super(ERROR_CODE, "Client %s was modified concurrently; the operation must be retried"
                .formatted(clientId));
    }
}
