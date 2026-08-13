package com.yam.funds.application.usecase;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.exception.FundNotFoundException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.Fund;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.IdempotencyOperation;
import com.yam.funds.domain.model.SubscriptionOutcome;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import com.yam.funds.domain.port.in.SubscribeToFundUseCase;
import com.yam.funds.domain.port.in.command.SubscribeToFundCommand;
import com.yam.funds.domain.port.in.result.TransactionReceipt;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.domain.port.out.EventPublisherPort;
import com.yam.funds.domain.port.out.FundRepositoryPort;
import com.yam.funds.domain.port.out.IdentifierGeneratorPort;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeToFundService implements SubscribeToFundUseCase {

    private final ClientRepositoryPort clientRepository;
    private final FundRepositoryPort fundRepository;
    private final TransactionRepositoryPort transactionRepository;
    private final EventPublisherPort eventPublisher;
    private final IdentifierGeneratorPort identifiers;
    private final IdempotentOperationExecutor idempotentExecutor;
    private final Clock clock;

    @Override
    public Mono<TransactionReceipt> subscribeToFund(final SubscribeToFundCommand command) {
        return idempotentExecutor.runExactlyOnce(
                        command.clientId(),
                        IdempotencyOperation.SUBSCRIBE,
                        command.idempotencyKey(),
                        fingerprintOf(command),
                        () -> openSubscription(command))
                .doFirst(() -> log.info("[subscribeToFund] [BEGIN] clientId={} fundId={} amount={}",
                        command.clientId(), command.fundId(), command.amount()))
                .doOnSuccess(receipt -> log.info(
                        "[subscribeToFund] [END OK] clientId={} fundId={} transactionId={} replayed={}",
                        command.clientId(), command.fundId(), receipt.transaction().id(), receipt.replayed()))
                .doOnError(error -> {
                    log.error("[subscribeToFund] [END EX] clientId={} fundId={} amount={}. Details: {}",
                            command.clientId(), command.fundId(), command.amount(), error.getMessage());
                    log.warn(error.getLocalizedMessage(), error);
                });
    }

    /**
     * Runs inside the executor's transaction. The rule checks live in the aggregate, so
     * this only loads what the aggregate needs and persists what it produced.
     */
    private Mono<FundTransaction> openSubscription(final SubscribeToFundCommand command) {
        return Mono.zip(loadClient(command), loadFund(command))
                .flatMap(loaded -> {
                    final Instant now = clock.instant();
                    final SubscriptionOutcome outcome = loaded.getT1().subscribeTo(
                            loaded.getT2(),
                            command.amount(),
                            identifiers.nextSubscriptionId(),
                            identifiers.nextTransactionId(),
                            now);
                    return persist(outcome, now);
                });
    }

    private Mono<FundTransaction> persist(final SubscriptionOutcome outcome, final Instant occurredAt) {
        final SubscriptionOpenedEvent event = SubscriptionOpenedEvent.from(
                outcome.client(), outcome.subscription(), outcome.transaction().id(), occurredAt);

        return clientRepository.save(outcome.client())
                .then(transactionRepository.save(outcome.transaction()))
                .then(eventPublisher.publish(event))
                .thenReturn(outcome.transaction());
    }

    private Mono<Client> loadClient(final SubscribeToFundCommand command) {
        return clientRepository.findById(command.clientId())
                .switchIfEmpty(Mono.error(() -> new ClientNotFoundException(command.clientId())));
    }

    private Mono<Fund> loadFund(final SubscribeToFundCommand command) {
        return fundRepository.findById(command.fundId())
                .switchIfEmpty(Mono.error(() -> new FundNotFoundException(command.fundId())));
    }

    private static String fingerprintOf(final SubscribeToFundCommand command) {
        return RequestFingerprints.of(
                command.clientId().value(),
                command.fundId().value(),
                command.amount().amount().toPlainString(),
                command.amount().currency().getCurrencyCode());
    }
}
