package com.yam.funds.domain.port.out;

import com.yam.funds.domain.model.IdempotencyRecord;

/**
 * Result of trying to reserve an idempotency key.
 *
 * <p>Sealed so callers must handle both outcomes: a duplicate is a normal, expected
 * result of a retry, not an error to be caught somewhere up the chain.
 */
public sealed interface IdempotencyReservation {

    /**
     * The key was free and is now held by this caller, which may proceed.
     *
     * @param record the reservation just stored
     */
    record Acquired(IdempotencyRecord record) implements IdempotencyReservation {
    }

    /**
     * The key was already taken. The existing record tells the caller what to do:
     * replay its stored response, reject a mismatched request, or reclaim the
     * reservation if its lease has expired.
     *
     * @param existing the reservation already held for this key
     */
    record AlreadyHeld(IdempotencyRecord existing) implements IdempotencyReservation {
    }
}
