package com.yam.funds.application.usecase;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.TransactionCursor;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.TransactionType;
import com.yam.funds.domain.port.in.query.TransactionHistoryQuery;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTransactionHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");
    private static final ClientId CLIENT_ID = ClientId.of("client-1");

    @Mock
    private ClientRepositoryPort clientRepository;
    @Mock
    private TransactionRepositoryPort transactionRepository;

    @InjectMocks
    private GetTransactionHistoryService service;

    private static Client aClient() {
        return Client.register(
                CLIENT_ID, "Ada Lovelace", "ada@example.com", "+573001112233",
                NotificationChannel.EMAIL, Money.cop(500_000));
    }

    private static FundTransaction aTransaction(final String id, final Instant occurredAt) {
        return new FundTransaction(
                TransactionId.of(id), CLIENT_ID, FundId.of("1"), "FPV_AM_PACTUAL_RECAUDADORA",
                SubscriptionId.of("sub-1"), TransactionType.OPENING,
                Money.cop(75_000), Money.cop(425_000), occurredAt);
    }

    @Test
    @DisplayName("returns the client's entries")
    void returnsHistory() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(aClient()));
        when(transactionRepository.findByClient(eq(CLIENT_ID), any(), anyInt()))
                .thenReturn(Flux.just(aTransaction("txn-2", NOW), aTransaction("txn-1", NOW.minusSeconds(60))));

        StepVerifier.create(service.findTransactionHistory(TransactionHistoryQuery.firstPage(CLIENT_ID)))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("passes the cursor and limit through to storage")
    void forwardsPaging() {
        final TransactionCursor cursor = new TransactionCursor(NOW, TransactionId.of("txn-2"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(aClient()));
        when(transactionRepository.findByClient(CLIENT_ID, cursor, 5)).thenReturn(Flux.empty());

        StepVerifier.create(service.findTransactionHistory(
                        new TransactionHistoryQuery(CLIENT_ID, cursor, 5)))
                .verifyComplete();

        verify(transactionRepository).findByClient(CLIENT_ID, cursor, 5);
    }

    @Test
    @DisplayName("reports an unknown client instead of returning an empty history")
    void failsOnUnknownClient() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.findTransactionHistory(TransactionHistoryQuery.firstPage(CLIENT_ID)))
                .expectError(ClientNotFoundException.class)
                .verify();

        verify(transactionRepository, never()).findByClient(any(), any(), anyInt());
    }

    @Test
    @DisplayName("caps an oversized page request")
    void capsLimit() {
        assertThat(new TransactionHistoryQuery(CLIENT_ID, null, 5_000).limit())
                .isEqualTo(TransactionHistoryQuery.MAX_LIMIT);
    }
}
