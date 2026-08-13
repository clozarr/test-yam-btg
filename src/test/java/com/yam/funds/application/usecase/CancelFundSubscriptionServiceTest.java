package com.yam.funds.application.usecase;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.exception.SubscriptionNotFoundException;
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
import com.yam.funds.domain.port.in.command.CancelFundSubscriptionCommand;
import com.yam.funds.domain.port.in.result.TransactionReceipt;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.domain.port.out.IdentifierGeneratorPort;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
class CancelFundSubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");
    private static final ClientId CLIENT_ID = ClientId.of("client-1");
    private static final FundId FUND_ID = FundId.of("1");

    private static final Fund RECAUDADORA = new Fund(
            FUND_ID, "FPV_AM_PACTUAL_RECAUDADORA", Money.cop(75_000), FundCategory.FPV, true);

    @Mock
    private ClientRepositoryPort clientRepository;
    @Mock
    private TransactionRepositoryPort transactionRepository;
    @Mock
    private IdentifierGeneratorPort identifiers;
    @Mock
    private IdempotentOperationExecutor idempotentExecutor;

    private CancelFundSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new CancelFundSubscriptionService(
                clientRepository,
                transactionRepository,
                identifiers,
                idempotentExecutor,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(idempotentExecutor.runExactlyOnce(any(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    final Supplier<Mono<FundTransaction>> work = invocation.getArgument(4);
                    return work.get().map(TransactionReceipt::executed);
                });

        when(identifiers.nextTransactionId()).thenReturn(TransactionId.of("txn-2"));
        when(clientRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(transactionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private static Client subscribedClient() {
        return Client.register(
                        CLIENT_ID, "Ada Lovelace", "ada@example.com", "+573001112233",
                        NotificationChannel.EMAIL, Money.cop(500_000))
                .subscribeTo(RECAUDADORA, Money.cop(75_000),
                        SubscriptionId.of("sub-1"), TransactionId.of("txn-1"), NOW)
                .client();
    }

    private static CancelFundSubscriptionCommand aCommand() {
        return new CancelFundSubscriptionCommand(CLIENT_ID, FUND_ID, "key-1");
    }

    @Test
    @DisplayName("returns the linked amount to the balance and records the entry")
    void cancelsSuccessfully() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(subscribedClient()));

        StepVerifier.create(service.cancelFundSubscription(aCommand()))
                .assertNext(receipt -> {
                    assertThat(receipt.transaction().type()).isEqualTo(TransactionType.CANCELLATION);
                    assertThat(receipt.transaction().amount()).isEqualTo(Money.cop(75_000));
                    assertThat(receipt.transaction().balanceAfter()).isEqualTo(Money.cop(500_000));
                })
                .verifyComplete();

        final ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(saved.capture());
        assertThat(saved.getValue().balance()).isEqualTo(Money.cop(500_000));
        assertThat(saved.getValue().activeSubscriptions()).isEmpty();
    }

    @Test
    @DisplayName("fails when the client does not exist")
    void failsOnUnknownClient() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.cancelFundSubscription(aCommand()))
                .expectError(ClientNotFoundException.class)
                .verify();

        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("writes nothing when the client holds no subscription to the fund")
    void failsOnMissingSubscription() {
        final Client withoutSubscriptions = Client.register(
                CLIENT_ID, "Ada Lovelace", "ada@example.com", "+573001112233",
                NotificationChannel.EMAIL, Money.cop(500_000));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(withoutSubscriptions));

        StepVerifier.create(service.cancelFundSubscription(aCommand()))
                .expectError(SubscriptionNotFoundException.class)
                .verify();

        verify(clientRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("does not consult the fund catalogue, so a withdrawn fund can still be cancelled")
    void doesNotReadTheCatalogue() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(subscribedClient()));

        StepVerifier.create(service.cancelFundSubscription(aCommand()))
                .assertNext(receipt -> assertThat(receipt.transaction().fundName())
                        .isEqualTo("FPV_AM_PACTUAL_RECAUDADORA"))
                .verifyComplete();
    }
}
