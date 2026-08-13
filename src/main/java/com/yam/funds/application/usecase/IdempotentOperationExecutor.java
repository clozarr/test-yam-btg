package com.yam.funds.application.usecase;

import com.yam.funds.config.FundsProperties;
import com.yam.funds.domain.exception.IdempotencyConflictException;
import com.yam.funds.domain.exception.OperationInProgressException;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.IdempotencyOperation;
import com.yam.funds.domain.model.IdempotencyRecord;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.port.in.result.TransactionReceipt;
import com.yam.funds.domain.port.out.IdempotencyPort;
import com.yam.funds.domain.port.out.IdempotencyReservation;
import com.yam.funds.domain.port.out.TransactionBoundaryPort;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Runs a money-moving operation exactly once per idempotency key.
 *
 * <p>Shared by the subscription and cancellation use cases, which differ in what they
 * do but not in how they must be protected.
 *
 * <p>The sequence is reserve-then-execute: the reservation is inserted before any work
 * starts, so a unique-key violation — not a read-then-write check — is what serialises
 * two duplicates arriving at the same instant. The reservation is promoted to completed
 * inside the same transaction as the operation, which closes the window where money
 * could have moved while the key still read as in progress. A business failure rolls the
 * whole thing back, including the reservation, so the caller may retry with the same key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentOperationExecutor {

    private final IdempotencyPort idempotencyPort;
    private final TransactionRepositoryPort transactionRepository;
    private final TransactionBoundaryPort transactionBoundary;
    private final FundsProperties properties;
    private final Clock clock;

    /**
     * @param clientId    client the key is scoped to
     * @param operation   operation the key is scoped to
     * @param clientKey   caller-supplied idempotency key
     * @param fingerprint digest of the request, used to detect a key reused with a
     *                    different payload
     * @param work        the operation to run, producing the ledger entry it recorded
     */
    public Mono<TransactionReceipt> runExactlyOnce(
            final ClientId clientId,
            final IdempotencyOperation operation,
            final String clientKey,
            final String fingerprint,
            final Supplier<Mono<FundTransaction>> work) {

        final Instant now = clock.instant();
        final IdempotencyRecord candidate = IdempotencyRecord.reserve(
                clientId, operation, clientKey, fingerprint,
                now, properties.idempotency().lease(), properties.idempotency().retention());

        return idempotencyPort.reserve(candidate)
                .flatMap(reservation -> switch (reservation) {
                    case IdempotencyReservation.Acquired(IdempotencyRecord reserved) ->
                            execute(reserved, work, now);
                    case IdempotencyReservation.AlreadyHeld(IdempotencyRecord existing) ->
                            resolveDuplicate(existing, candidate, work, now);
                });
    }

    private Mono<TransactionReceipt> execute(
            final IdempotencyRecord reservation, final Supplier<Mono<FundTransaction>> work, final Instant now) {

        return transactionBoundary.executeInTransaction(
                        Mono.defer(work)
                                .flatMap(transaction -> idempotencyPort
                                        .complete(reservation.complete(transaction.id().value(), now))
                                        .thenReturn(transaction)))
                .map(TransactionReceipt::executed)
                // The reservation was inserted before the transaction opened, so the
                // rollback does not undo it. Releasing it here is what lets a caller whose
                // request was refused fix it and retry with the same key, instead of being
                // locked out until the lease expires.
                .onErrorResume(failure -> releaseQuietly(reservation).then(Mono.error(failure)));
    }

    /**
     * A failure to release must not replace the error the caller actually needs to see;
     * the lease expiry is the backstop if this does not land.
     */
    private Mono<Void> releaseQuietly(final IdempotencyRecord reservation) {
        return idempotencyPort.release(reservation)
                .onErrorResume(releaseFailure -> {
                    log.warn("[runExactlyOnce] could not release reservation {}. Details: {}",
                            reservation.key(), releaseFailure.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Decides what a repeated key means: a replay, a caller bug, a genuinely concurrent
     * request, or a reservation abandoned by an instance that died mid-flight.
     */
    private Mono<TransactionReceipt> resolveDuplicate(
            final IdempotencyRecord existing,
            final IdempotencyRecord candidate,
            final Supplier<Mono<FundTransaction>> work,
            final Instant now) {

        if (!existing.hasSameRequestAs(candidate.requestFingerprint())) {
            return Mono.error(new IdempotencyConflictException(existing.key()));
        }
        if (existing.isCompleted()) {
            return replay(existing);
        }
        if (!existing.isLeaseExpired(now)) {
            return Mono.error(new OperationInProgressException(existing.key()));
        }
        return idempotencyPort.reclaimExpired(candidate)
                .flatMap(reclaimed -> reclaimed
                        ? execute(candidate, work, now)
                        : Mono.error(new OperationInProgressException(existing.key())));
    }

    private Mono<TransactionReceipt> replay(final IdempotencyRecord completed) {
        log.debug("[runExactlyOnce] replaying stored result for key {}", completed.key());
        return transactionRepository.findById(TransactionId.of(completed.responsePayload()))
                .map(TransactionReceipt::replayed)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Idempotency key %s points at transaction %s, which is missing from the ledger"
                                .formatted(completed.key(), completed.responsePayload()))));
    }
}
