package com.yam.funds.application.usecase;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.IdempotencyOperation;
import com.yam.funds.domain.model.SubscriptionOutcome;
import com.yam.funds.domain.port.in.CancelFundSubscriptionUseCase;
import com.yam.funds.domain.port.in.command.CancelFundSubscriptionCommand;
import com.yam.funds.domain.port.in.result.TransactionReceipt;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.domain.port.out.IdentifierGeneratorPort;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelFundSubscriptionService implements CancelFundSubscriptionUseCase {

    private final ClientRepositoryPort clientRepository;
    private final TransactionRepositoryPort transactionRepository;
    private final IdentifierGeneratorPort identifiers;
    private final IdempotentOperationExecutor idempotentExecutor;
    private final Clock clock;

    @Override
    public Mono<TransactionReceipt> cancelFundSubscription(final CancelFundSubscriptionCommand command) {
        return idempotentExecutor.runExactlyOnce(
                        command.clientId(),
                        IdempotencyOperation.CANCEL,
                        command.idempotencyKey(),
                        fingerprintOf(command),
                        () -> closeSubscription(command))
                .doFirst(() -> log.info("[cancelFundSubscription] [BEGIN] clientId={} fundId={}",
                        command.clientId(), command.fundId()))
                .doOnSuccess(receipt -> log.info(
                        "[cancelFundSubscription] [END OK] clientId={} fundId={} transactionId={} replayed={}",
                        command.clientId(), command.fundId(), receipt.transaction().id(), receipt.replayed()))
                .doOnError(error -> {
                    log.error("[cancelFundSubscription] [END EX] clientId={} fundId={}. Details: {}",
                            command.clientId(), command.fundId(), error.getMessage());
                    log.warn(error.getLocalizedMessage(), error);
                });
    }

    /**
     * The fund catalogue is not consulted: the subscription carries the fund's name, so
     * a cancellation stays possible even for a fund that has since been withdrawn.
     */
    private Mono<FundTransaction> closeSubscription(final CancelFundSubscriptionCommand command) {
        return clientRepository.findById(command.clientId())
                .switchIfEmpty(Mono.error(() -> new ClientNotFoundException(command.clientId())))
                .flatMap(client -> {
                    final SubscriptionOutcome outcome = client.cancelSubscriptionTo(
                            command.fundId(), identifiers.nextTransactionId(), clock.instant());
                    return clientRepository.save(outcome.client())
                            .then(transactionRepository.save(outcome.transaction()));
                });
    }

    private static String fingerprintOf(final CancelFundSubscriptionCommand command) {
        return RequestFingerprints.of(command.clientId().value(), command.fundId().value());
    }
}
