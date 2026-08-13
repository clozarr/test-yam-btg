package com.yam.funds.domain.port.out;

import com.yam.funds.domain.model.IdempotencyRecord;
import reactor.core.publisher.Mono;

/**
 * Storage backing the exactly-once guarantee on money-moving operations.
 *
 * <p>Implementations must enforce uniqueness on {@link IdempotencyRecord#key()} at the
 * storage level. That uniqueness constraint — not a read-then-write check in the use
 * case — is what serialises concurrent duplicates: two requests arriving at the same
 * instant with the same key would both pass a "does it exist?" check.
 */
public interface IdempotencyPort {

    /**
     * Atomically claims the key for this caller.
     *
     * @param candidate the reservation to insert, in progress and with its lease set
     * @return {@link IdempotencyReservation.Acquired} if the key was free, otherwise
     *         {@link IdempotencyReservation.AlreadyHeld} carrying the existing record
     */
    Mono<IdempotencyReservation> reserve(IdempotencyRecord candidate);

    /**
     * Marks the reservation completed and stores the response to replay.
     *
     * <p>Must be called inside the same transaction as the operation it covers,
     * otherwise a crash in between leaves money moved under a key that still reads as
     * in progress.
     *
     * @param record the reservation, completed and carrying its response payload
     * @return completion once stored
     */
    Mono<Void> complete(IdempotencyRecord record);

    /**
     * Takes over a reservation whose lease expired, presumably left behind by a crashed
     * instance.
     *
     * @param candidate the new reservation to put in place of the abandoned one
     * @return {@code true} if the abandoned reservation was successfully taken over;
     *         {@code false} if another caller got there first or it is no longer expired
     */
    Mono<Boolean> reclaimExpired(IdempotencyRecord candidate);
}
