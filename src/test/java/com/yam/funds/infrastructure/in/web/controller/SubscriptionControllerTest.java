package com.yam.funds.infrastructure.in.web.controller;

import com.yam.funds.domain.exception.AlreadySubscribedException;
import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.exception.InsufficientBalanceException;
import com.yam.funds.domain.exception.MinimumAmountNotMetException;
import com.yam.funds.domain.exception.OperationInProgressException;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.TransactionType;
import com.yam.funds.domain.port.in.CancelFundSubscriptionUseCase;
import com.yam.funds.domain.port.in.SubscribeToFundUseCase;
import com.yam.funds.domain.port.in.command.SubscribeToFundCommand;
import com.yam.funds.domain.port.in.result.TransactionReceipt;
import com.yam.funds.infrastructure.in.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the HTTP contract with a standalone controller setup.
 *
 * <p>Bound directly to the controller rather than through {@code @WebFluxTest}: this
 * covers request binding, validation and error mapping without pulling in security
 * auto-configuration, which is verified separately.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");
    private static final String CLIENT_ID = "client-1";
    private static final String FUND_ID = "1";
    private static final String SUBSCRIPTIONS_URI = "/api/v1/clients/client-1/subscriptions/1";

    @Mock
    private SubscribeToFundUseCase subscribeToFundUseCase;
    @Mock
    private CancelFundSubscriptionUseCase cancelFundSubscriptionUseCase;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        final SubscriptionController controller = new SubscriptionController(
                subscribeToFundUseCase, cancelFundSubscriptionUseCase, Currency.getInstance("COP"));

        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static FundTransaction aTransaction(final TransactionType type) {
        return new FundTransaction(
                TransactionId.of("txn-1"), ClientId.of(CLIENT_ID), FundId.of(FUND_ID),
                "FPV_AM_PACTUAL_RECAUDADORA", SubscriptionId.of("sub-1"), type,
                Money.cop(75_000), Money.cop(425_000), NOW);
    }

    private WebTestClient.RequestHeadersSpec<?> subscribeRequest(final String body) {
        return webTestClient.post().uri(SUBSCRIPTIONS_URI)
                .header("Idempotency-Key", "idem-key-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }

    @Test
    @DisplayName("returns 201 with the ledger entry on a successful subscription")
    void subscribesSuccessfully() {
        when(subscribeToFundUseCase.subscribeToFund(any()))
                .thenReturn(Mono.just(TransactionReceipt.executed(aTransaction(TransactionType.OPENING))));

        subscribeRequest("{\"amount\": 75000.00}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.transaction.transactionId").isEqualTo("txn-1")
                .jsonPath("$.transaction.type").isEqualTo("OPENING")
                .jsonPath("$.transaction.balanceAfter.amount").isEqualTo(425000.00)
                .jsonPath("$.transaction.balanceAfter.currency").isEqualTo("COP")
                .jsonPath("$.replayed").isEqualTo(false);
    }

    @Test
    @DisplayName("passes the path, body and idempotency key through to the use case")
    void buildsTheCommand() {
        when(subscribeToFundUseCase.subscribeToFund(any()))
                .thenReturn(Mono.just(TransactionReceipt.executed(aTransaction(TransactionType.OPENING))));

        subscribeRequest("{\"amount\": 75000.00}").exchange().expectStatus().isCreated();

        final ArgumentCaptor<SubscribeToFundCommand> command =
                ArgumentCaptor.forClass(SubscribeToFundCommand.class);
        verify(subscribeToFundUseCase).subscribeToFund(command.capture());
        assertThat(command.getValue().clientId()).isEqualTo(ClientId.of(CLIENT_ID));
        assertThat(command.getValue().fundId()).isEqualTo(FundId.of(FUND_ID));
        assertThat(command.getValue().amount()).isEqualTo(Money.cop(new BigDecimal("75000.00")));
        assertThat(command.getValue().idempotencyKey()).isEqualTo("idem-key-0001");
    }

    @Test
    @DisplayName("flags a replayed result so the caller can tell it apart from a fresh one")
    void flagsReplayedResult() {
        when(subscribeToFundUseCase.subscribeToFund(any()))
                .thenReturn(Mono.just(TransactionReceipt.replayed(aTransaction(TransactionType.OPENING))));

        subscribeRequest("{\"amount\": 75000.00}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.replayed").isEqualTo(true);
    }

    @Test
    @DisplayName("rejects a subscription with no idempotency key rather than moving money")
    void rejectsMissingIdempotencyKey() {
        webTestClient.post().uri(SUBSCRIPTIONS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\": 75000.00}")
                .exchange()
                .expectStatus().isBadRequest();

        verify(subscribeToFundUseCase, never()).subscribeToFund(any());
    }

    @Test
    @DisplayName("rejects a non-positive amount before it reaches the domain")
    void rejectsNonPositiveAmount() {
        subscribeRequest("{\"amount\": 0}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("VALIDATION_FAILED");

        verify(subscribeToFundUseCase, never()).subscribeToFund(any());
    }

    @Test
    @DisplayName("returns 422 and the exact business wording when the balance is short")
    void mapsInsufficientBalance() {
        when(subscribeToFundUseCase.subscribeToFund(any()))
                .thenReturn(Mono.error(new InsufficientBalanceException("FDO-ACCIONES")));

        subscribeRequest("{\"amount\": 250000.00}")
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INSUFFICIENT_BALANCE")
                .jsonPath("$.messages[0]")
                .isEqualTo("No tiene saldo disponible para vincularse al fondo FDO-ACCIONES");
    }

    @Test
    @DisplayName("returns 422 when the amount is below the fund minimum")
    void mapsMinimumAmountNotMet() {
        when(subscribeToFundUseCase.subscribeToFund(any()))
                .thenReturn(Mono.error(new MinimumAmountNotMetException(
                        "FDO-ACCIONES", Money.cop(250_000), Money.cop(1_000))));

        subscribeRequest("{\"amount\": 1000.00}")
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.errorCode").isEqualTo("MINIMUM_AMOUNT_NOT_MET");
    }

    @Test
    @DisplayName("returns 404 for an unknown client")
    void mapsClientNotFound() {
        when(subscribeToFundUseCase.subscribeToFund(any()))
                .thenReturn(Mono.error(new ClientNotFoundException(ClientId.of(CLIENT_ID))));

        subscribeRequest("{\"amount\": 75000.00}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.errorCode").isEqualTo("CLIENT_NOT_FOUND");
    }

    @Test
    @DisplayName("returns 409 when the client is already linked to the fund")
    void mapsAlreadySubscribed() {
        when(subscribeToFundUseCase.subscribeToFund(any()))
                .thenReturn(Mono.error(new AlreadySubscribedException(
                        ClientId.of(CLIENT_ID), "FPV_AM_PACTUAL_RECAUDADORA")));

        subscribeRequest("{\"amount\": 75000.00}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("ALREADY_SUBSCRIBED");
    }

    @Test
    @DisplayName("returns 409 with Retry-After while an identical request is still running")
    void mapsOperationInProgress() {
        when(subscribeToFundUseCase.subscribeToFund(any()))
                .thenReturn(Mono.error(new OperationInProgressException("client-1:SUBSCRIBE:idem-key-0001")));

        subscribeRequest("{\"amount\": 75000.00}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().exists("Retry-After")
                .expectBody().jsonPath("$.errorCode").isEqualTo("OPERATION_IN_PROGRESS");
    }

    @Test
    @DisplayName("cancels a subscription and reports the returned amount")
    void cancelsSuccessfully() {
        when(cancelFundSubscriptionUseCase.cancelFundSubscription(any()))
                .thenReturn(Mono.just(TransactionReceipt.executed(
                        aTransaction(TransactionType.CANCELLATION))));

        webTestClient.delete().uri(SUBSCRIPTIONS_URI)
                .header("Idempotency-Key", "idem-key-0002")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.transaction.type").isEqualTo("CANCELLATION");
    }
}
