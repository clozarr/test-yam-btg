package com.yam.funds.application.usecase;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.exception.FundNotFoundException;
import com.yam.funds.domain.exception.InsufficientBalanceException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.Fund;
import com.yam.funds.domain.model.FundCategory;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.TransactionType;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import com.yam.funds.domain.port.in.command.SubscribeToFundCommand;
import com.yam.funds.domain.port.in.result.TransactionReceipt;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.domain.port.out.EventPublisherPort;
import com.yam.funds.domain.port.out.FundRepositoryPort;
import com.yam.funds.domain.port.out.IdentifierGeneratorPort;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscribeToFundServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");
    private static final ClientId CLIENT_ID = ClientId.of("client-1");
    private static final FundId FUND_ID = FundId.of("1");

    private static final Fund RECAUDADORA = new Fund(
            FUND_ID, "FPV_AM_PACTUAL_RECAUDADORA", Money.cop(75_000), FundCategory.FPV, true);

    @Mock
    private ClientRepositoryPort clientRepository;
    @Mock
    private FundRepositoryPort fundRepository;
    @Mock
    private TransactionRepositoryPort transactionRepository;
    @Mock
    private EventPublisherPort eventPublisher;
    @Mock
    private IdentifierGeneratorPort identifiers;
    @Mock
    private IdempotentOperationExecutor idempotentExecutor;

    private SubscribeToFundService service;

    @BeforeEach
    void setUp() {
        service = new SubscribeToFundService(
                clientRepository,
                fundRepository,
                transactionRepository,
                eventPublisher,
                identifiers,
                idempotentExecutor,
                Clock.fixed(NOW, ZoneOffset.UTC));

        // The executor is exercised on its own; here it simply runs the work it is given.
        when(idempotentExecutor.runExactlyOnce(any(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    final Supplier<Mono<FundTransaction>> work = invocation.getArgument(4);
                    return work.get().map(TransactionReceipt::executed);
                });

        when(identifiers.nextSubscriptionId()).thenReturn(SubscriptionId.of("sub-1"));
        when(identifiers.nextTransactionId()).thenReturn(TransactionId.of("txn-1"));
        when(clientRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(transactionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
    }

    private static Client aClient(final Money balance) {
        return Client.register(
                CLIENT_ID, "Ada Lovelace", "ada@example.com", "+573001112233",
                NotificationChannel.EMAIL, balance);
    }

    private static SubscribeToFundCommand aCommand(final Money amount) {
        return new SubscribeToFundCommand(CLIENT_ID, FUND_ID, amount, "key-1");
    }

    @Test
    @DisplayName("debits the balance, records the ledger entry and publishes the event")
    void subscribesSuccessfully() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(aClient(Money.cop(500_000))));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(RECAUDADORA));

        StepVerifier.create(service.subscribeToFund(aCommand(Money.cop(75_000))))
                .assertNext(receipt -> {
                    assertThat(receipt.replayed()).isFalse();
                    assertThat(receipt.transaction().type()).isEqualTo(TransactionType.OPENING);
                    assertThat(receipt.transaction().balanceAfter()).isEqualTo(Money.cop(425_000));
                    assertThat(receipt.transaction().occurredAt()).isEqualTo(NOW);
                })
                .verifyComplete();

        final ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(saved.capture());
        assertThat(saved.getValue().balance()).isEqualTo(Money.cop(425_000));
        assertThat(saved.getValue().isSubscribedTo(FUND_ID)).isTrue();
    }

    @Test
    @DisplayName("publishes an event carrying the client's chosen channel and contact details")
    void publishesNotificationEvent() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(aClient(Money.cop(500_000))));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(RECAUDADORA));

        service.subscribeToFund(aCommand(Money.cop(75_000))).block();

        final ArgumentCaptor<SubscriptionOpenedEvent> event =
                ArgumentCaptor.forClass(SubscriptionOpenedEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(event.getValue().email()).isEqualTo("ada@example.com");
        assertThat(event.getValue().fundName()).isEqualTo("FPV_AM_PACTUAL_RECAUDADORA");
        assertThat(event.getValue().transactionId()).isEqualTo(TransactionId.of("txn-1"));
    }

    @Test
    @DisplayName("fails when the client does not exist")
    void failsOnUnknownClient() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.empty());
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(RECAUDADORA));

        StepVerifier.create(service.subscribeToFund(aCommand(Money.cop(75_000))))
                .expectError(ClientNotFoundException.class)
                .verify();

        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("fails when the fund is not in the catalogue")
    void failsOnUnknownFund() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(aClient(Money.cop(500_000))));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.subscribeToFund(aCommand(Money.cop(75_000))))
                .expectError(FundNotFoundException.class)
                .verify();

        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("writes nothing when the balance does not cover the amount")
    void writesNothingOnInsufficientBalance() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(aClient(Money.cop(10_000))));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(RECAUDADORA));

        StepVerifier.create(service.subscribeToFund(aCommand(Money.cop(75_000))))
                .expectErrorMessage("No tiene saldo disponible para vincularse al fondo "
                        + "FPV_AM_PACTUAL_RECAUDADORA")
                .verify();

        verify(clientRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("reports the failure as insufficient balance rather than a generic error")
    void reportsInsufficientBalanceType() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(aClient(Money.cop(10_000))));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(RECAUDADORA));

        StepVerifier.create(service.subscribeToFund(aCommand(Money.cop(75_000))))
                .expectError(InsufficientBalanceException.class)
                .verify();
    }
}
