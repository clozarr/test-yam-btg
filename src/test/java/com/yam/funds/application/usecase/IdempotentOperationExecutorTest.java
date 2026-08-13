package com.yam.funds.application.usecase;

import com.yam.funds.config.FundsProperties;
import com.yam.funds.config.FundsPropertiesFixture;
import com.yam.funds.domain.exception.IdempotencyConflictException;
import com.yam.funds.domain.exception.InsufficientBalanceException;
import com.yam.funds.domain.exception.OperationInProgressException;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.IdempotencyOperation;
import com.yam.funds.domain.model.IdempotencyRecord;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.TransactionType;
import com.yam.funds.domain.port.out.IdempotencyPort;
import com.yam.funds.domain.port.out.IdempotencyReservation;
import com.yam.funds.domain.port.out.TransactionBoundaryPort;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentOperationExecutorTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final ClientId CLIENT_ID = ClientId.of("client-1");
    private static final String CLIENT_KEY = "key-1";

    @Mock
    private IdempotencyPort idempotencyPort;
    @Mock
    private TransactionRepositoryPort transactionRepository;
    @Mock
    private TransactionBoundaryPort transactionBoundary;

    private IdempotentOperationExecutor executor;

    @BeforeEach
    void setUp() {
        final FundsProperties properties = FundsPropertiesFixture.withIdempotency(RETENTION, LEASE);
        executor = new IdempotentOperationExecutor(
                idempotencyPort,
                transactionRepository,
                transactionBoundary,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static FundTransaction aTransaction() {
        return new FundTransaction(
                TransactionId.of("txn-1"),
                CLIENT_ID,
                FundId.of("1"),
                "FPV_AM_PACTUAL_RECAUDADORA",
                SubscriptionId.of("sub-1"),
                TransactionType.OPENING,
                Money.cop(75_000),
                Money.cop(425_000),
                NOW);
    }

    private static IdempotencyRecord existingRecord(final String fingerprint) {
        return IdempotencyRecord.reserve(
                CLIENT_ID, IdempotencyOperation.SUBSCRIBE, CLIENT_KEY, fingerprint, NOW, LEASE, RETENTION);
    }

    private void passThroughTransaction() {
        when(transactionBoundary.executeInTransaction(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Mono<com.yam.funds.domain.port.in.result.TransactionReceipt> run(
            final Supplier<Mono<FundTransaction>> work) {
        return executor.runExactlyOnce(
                CLIENT_ID, IdempotencyOperation.SUBSCRIBE, CLIENT_KEY, "fingerprint-a", work);
    }

    @Test
    @DisplayName("runs the operation when the key is free and marks the reservation completed")
    void executesWhenKeyIsFree() {
        final IdempotencyRecord reservation = existingRecord("fingerprint-a");
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.Acquired(reservation)));
        when(idempotencyPort.complete(any())).thenReturn(Mono.empty());
        passThroughTransaction();

        StepVerifier.create(run(() -> Mono.just(aTransaction())))
                .assertNext(receipt -> {
                    assertThat(receipt.replayed()).isFalse();
                    assertThat(receipt.transaction().id()).isEqualTo(TransactionId.of("txn-1"));
                })
                .verifyComplete();

        final ArgumentCaptor<IdempotencyRecord> completed = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyPort).complete(completed.capture());
        assertThat(completed.getValue().isCompleted()).isTrue();
        assertThat(completed.getValue().responsePayload()).isEqualTo("txn-1");
    }

    @Test
    @DisplayName("commits the operation and the completed reservation in the same transaction")
    void completesInsideTheTransaction() {
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.Acquired(existingRecord("fingerprint-a"))));
        when(idempotencyPort.complete(any())).thenReturn(Mono.empty());
        passThroughTransaction();

        run(() -> Mono.just(aTransaction())).block();

        // Everything the executor did was handed to the transaction boundary exactly once;
        // nothing escaped it, which is what stops money moving under an in-progress key.
        verify(transactionBoundary).executeInTransaction(any());
    }

    @Test
    @DisplayName("replays the stored result when the same request repeats")
    void replaysCompletedRecord() {
        final IdempotencyRecord completed = existingRecord("fingerprint-a").complete("txn-1", NOW);
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.AlreadyHeld(completed)));
        when(transactionRepository.findById(TransactionId.of("txn-1")))
                .thenReturn(Mono.just(aTransaction()));

        final AtomicInteger executions = new AtomicInteger();

        StepVerifier.create(run(() -> {
                    executions.incrementAndGet();
                    return Mono.just(aTransaction());
                }))
                .assertNext(receipt -> {
                    assertThat(receipt.replayed()).isTrue();
                    assertThat(receipt.transaction().id()).isEqualTo(TransactionId.of("txn-1"));
                })
                .verifyComplete();

        assertThat(executions).hasValue(0);
        verify(transactionBoundary, never()).executeInTransaction(any());
    }

    @Test
    @DisplayName("rejects a key reused with a different request instead of replaying it")
    void rejectsFingerprintMismatch() {
        final IdempotencyRecord completed = existingRecord("fingerprint-b").complete("txn-1", NOW);
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.AlreadyHeld(completed)));

        StepVerifier.create(run(() -> Mono.just(aTransaction())))
                .expectError(IdempotencyConflictException.class)
                .verify();

        verify(transactionBoundary, never()).executeInTransaction(any());
    }

    @Test
    @DisplayName("rejects a concurrent request whose lease is still valid")
    void rejectsInFlightDuplicate() {
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.AlreadyHeld(existingRecord("fingerprint-a"))));

        StepVerifier.create(run(() -> Mono.just(aTransaction())))
                .expectError(OperationInProgressException.class)
                .verify();

        verify(transactionBoundary, never()).executeInTransaction(any());
    }

    @Test
    @DisplayName("takes over a reservation abandoned by a crashed instance")
    void reclaimsExpiredLease() {
        final IdempotencyRecord abandoned = IdempotencyRecord.reserve(
                CLIENT_ID, IdempotencyOperation.SUBSCRIBE, CLIENT_KEY, "fingerprint-a",
                NOW.minus(Duration.ofMinutes(5)), LEASE, RETENTION);
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.AlreadyHeld(abandoned)));
        when(idempotencyPort.reclaimExpired(any())).thenReturn(Mono.just(true));
        when(idempotencyPort.complete(any())).thenReturn(Mono.empty());
        passThroughTransaction();

        StepVerifier.create(run(() -> Mono.just(aTransaction())))
                .assertNext(receipt -> assertThat(receipt.replayed()).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("stands down when another instance reclaims the abandoned reservation first")
    void yieldsWhenReclaimIsLost() {
        final IdempotencyRecord abandoned = IdempotencyRecord.reserve(
                CLIENT_ID, IdempotencyOperation.SUBSCRIBE, CLIENT_KEY, "fingerprint-a",
                NOW.minus(Duration.ofMinutes(5)), LEASE, RETENTION);
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.AlreadyHeld(abandoned)));
        when(idempotencyPort.reclaimExpired(any())).thenReturn(Mono.just(false));

        StepVerifier.create(run(() -> Mono.just(aTransaction())))
                .expectError(OperationInProgressException.class)
                .verify();

        verify(transactionBoundary, never()).executeInTransaction(any());
    }

    @Test
    @DisplayName("propagates a business failure without completing the reservation")
    void propagatesBusinessFailure() {
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.Acquired(existingRecord("fingerprint-a"))));
        when(idempotencyPort.release(any())).thenReturn(Mono.empty());
        passThroughTransaction();

        StepVerifier.create(run(() -> Mono.error(new InsufficientBalanceException("FDO-ACCIONES"))))
                .expectError(InsufficientBalanceException.class)
                .verify();

        verify(idempotencyPort, never()).complete(any());
    }

    @Test
    @DisplayName("frees the key when the operation is refused, so the caller can correct and retry")
    void releasesReservationOnBusinessFailure() {
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.Acquired(existingRecord("fingerprint-a"))));
        when(idempotencyPort.release(any())).thenReturn(Mono.empty());
        passThroughTransaction();

        StepVerifier.create(run(() -> Mono.error(new InsufficientBalanceException("FDO-ACCIONES"))))
                .expectError(InsufficientBalanceException.class)
                .verify();

        // The reservation is inserted before the transaction opens, so the rollback does
        // not remove it; without this compensating delete the caller would be locked out
        // of their own key until the lease expired.
        verify(idempotencyPort).release(any());
    }

    @Test
    @DisplayName("reports the original failure even if releasing the reservation fails")
    void keepsOriginalErrorWhenReleaseFails() {
        when(idempotencyPort.reserve(any()))
                .thenReturn(Mono.just(new IdempotencyReservation.Acquired(existingRecord("fingerprint-a"))));
        when(idempotencyPort.release(any()))
                .thenReturn(Mono.error(new IllegalStateException("storage unavailable")));
        passThroughTransaction();

        StepVerifier.create(run(() -> Mono.error(new InsufficientBalanceException("FDO-ACCIONES"))))
                .expectError(InsufficientBalanceException.class)
                .verify();
    }
}
