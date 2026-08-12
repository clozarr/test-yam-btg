package com.yam.funds.domain.model;

import com.yam.funds.domain.exception.AlreadySubscribedException;
import com.yam.funds.domain.exception.FundNotAvailableException;
import com.yam.funds.domain.exception.InsufficientBalanceException;
import com.yam.funds.domain.exception.MinimumAmountNotMetException;
import com.yam.funds.domain.exception.SubscriptionNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");
    private static final Money INITIAL_BALANCE = Money.cop(500_000);

    private static final Fund RECAUDADORA = new Fund(
            FundId.of("1"), "FPV_AM_PACTUAL_RECAUDADORA", Money.cop(75_000), FundCategory.FPV, true);
    private static final Fund ACCIONES = new Fund(
            FundId.of("4"), "FDO-ACCIONES", Money.cop(250_000), FundCategory.FIC, true);

    private static Client aClient() {
        return Client.register(
                ClientId.of("client-1"),
                "Ada Lovelace",
                "ada@example.com",
                "+573001112233",
                NotificationChannel.EMAIL,
                INITIAL_BALANCE);
    }

    private static SubscriptionOutcome subscribe(final Client client, final Fund fund, final Money amount) {
        return client.subscribeTo(
                fund, amount, SubscriptionId.of("sub-1"), TransactionId.of("txn-1"), NOW);
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("starts with the opening balance and no subscriptions")
        void startsClean() {
            final Client client = aClient();

            assertThat(client.balance()).isEqualTo(INITIAL_BALANCE);
            assertThat(client.activeSubscriptions()).isEmpty();
            assertThat(client.version()).isNull();
        }
    }

    @Nested
    @DisplayName("subscribing to a fund")
    class Subscribing {

        @Test
        @DisplayName("debits the linked amount and records the subscription")
        void debitsBalance() {
            final SubscriptionOutcome outcome = subscribe(aClient(), RECAUDADORA, Money.cop(75_000));

            assertThat(outcome.client().balance()).isEqualTo(Money.cop(425_000));
            assertThat(outcome.client().isSubscribedTo(RECAUDADORA.id())).isTrue();
            assertThat(outcome.subscription().status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(outcome.subscription().linkedAmount()).isEqualTo(Money.cop(75_000));
            assertThat(outcome.subscription().fundName()).isEqualTo(RECAUDADORA.name());
        }

        @Test
        @DisplayName("produces an opening ledger entry carrying the resulting balance")
        void producesLedgerEntry() {
            final SubscriptionOutcome outcome = subscribe(aClient(), RECAUDADORA, Money.cop(75_000));
            final FundTransaction transaction = outcome.transaction();

            assertThat(transaction.id()).isEqualTo(TransactionId.of("txn-1"));
            assertThat(transaction.type()).isEqualTo(TransactionType.OPENING);
            assertThat(transaction.amount()).isEqualTo(Money.cop(75_000));
            assertThat(transaction.balanceAfter()).isEqualTo(Money.cop(425_000));
            assertThat(transaction.occurredAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("leaves the original aggregate untouched")
        void doesNotMutateOriginal() {
            final Client original = aClient();

            subscribe(original, RECAUDADORA, Money.cop(75_000));

            assertThat(original.balance()).isEqualTo(INITIAL_BALANCE);
            assertThat(original.activeSubscriptions()).isEmpty();
        }

        @Test
        @DisplayName("accepts an amount above the fund minimum")
        void acceptsAmountAboveMinimum() {
            final SubscriptionOutcome outcome = subscribe(aClient(), RECAUDADORA, Money.cop(100_000));

            assertThat(outcome.client().balance()).isEqualTo(Money.cop(400_000));
        }

        @Test
        @DisplayName("rejects an amount below the fund minimum")
        void rejectsAmountBelowMinimum() {
            final Client client = aClient();

            assertThatThrownBy(() -> subscribe(client, ACCIONES, Money.cop(100_000)))
                    .isInstanceOf(MinimumAmountNotMetException.class)
                    .hasMessageContaining("FDO-ACCIONES");
        }

        @Test
        @DisplayName("reports insufficient balance with the exact business wording")
        void reportsInsufficientBalance() {
            // Two subscriptions of 250.000 exhaust the 500.000 opening balance; a third
            // one against a fund requiring 250.000 cannot be covered.
            final Client client = aClient()
                    .subscribeTo(ACCIONES, Money.cop(250_000),
                            SubscriptionId.of("s1"), TransactionId.of("t1"), NOW)
                    .client()
                    .subscribeTo(RECAUDADORA, Money.cop(250_000),
                            SubscriptionId.of("s2"), TransactionId.of("t2"), NOW)
                    .client();
            final Fund dinamica = new Fund(
                    FundId.of("5"), "FPV_AM_PACTUAL_DINAMICA", Money.cop(100_000), FundCategory.FPV, true);

            assertThat(client.balance()).isEqualTo(Money.zero());
            assertThatThrownBy(() -> client.subscribeTo(dinamica, Money.cop(100_000),
                    SubscriptionId.of("s3"), TransactionId.of("t3"), NOW))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessage("No tiene saldo disponible para vincularse al fondo FPV_AM_PACTUAL_DINAMICA");
        }

        @Test
        @DisplayName("rejects a second active subscription to the same fund")
        void rejectsDuplicateSubscription() {
            final Client subscribed = subscribe(aClient(), RECAUDADORA, Money.cop(75_000)).client();

            assertThatThrownBy(() -> subscribed.subscribeTo(RECAUDADORA, Money.cop(75_000),
                    SubscriptionId.of("s2"), TransactionId.of("t2"), NOW))
                    .isInstanceOf(AlreadySubscribedException.class);
        }

        @Test
        @DisplayName("rejects a fund closed to new subscriptions")
        void rejectsInactiveFund() {
            final Fund closed = new Fund(
                    FundId.of("9"), "CLOSED_FUND", Money.cop(1_000), FundCategory.FIC, false);
            final Client client = aClient();

            assertThatThrownBy(() -> subscribe(client, closed, Money.cop(1_000)))
                    .isInstanceOf(FundNotAvailableException.class);
        }
    }

    @Nested
    @DisplayName("cancelling a subscription")
    class Cancelling {

        @Test
        @DisplayName("returns the linked amount to the balance")
        void returnsLinkedAmount() {
            final Client subscribed = subscribe(aClient(), RECAUDADORA, Money.cop(75_000)).client();

            final SubscriptionOutcome outcome = subscribed.cancelSubscriptionTo(
                    RECAUDADORA.id(), TransactionId.of("txn-2"), NOW);

            assertThat(outcome.client().balance()).isEqualTo(INITIAL_BALANCE);
            assertThat(outcome.client().activeSubscriptions()).isEmpty();
            assertThat(outcome.subscription().status()).isEqualTo(SubscriptionStatus.CANCELLED);
            assertThat(outcome.subscription().cancelledAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("produces a cancellation ledger entry")
        void producesLedgerEntry() {
            final Client subscribed = subscribe(aClient(), RECAUDADORA, Money.cop(75_000)).client();

            final FundTransaction transaction = subscribed
                    .cancelSubscriptionTo(RECAUDADORA.id(), TransactionId.of("txn-2"), NOW)
                    .transaction();

            assertThat(transaction.type()).isEqualTo(TransactionType.CANCELLATION);
            assertThat(transaction.amount()).isEqualTo(Money.cop(75_000));
            assertThat(transaction.balanceAfter()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("frees the fund so the client can subscribe again")
        void allowsResubscription() {
            final Client cancelled = subscribe(aClient(), RECAUDADORA, Money.cop(75_000))
                    .client()
                    .cancelSubscriptionTo(RECAUDADORA.id(), TransactionId.of("txn-2"), NOW)
                    .client();

            final SubscriptionOutcome outcome = cancelled.subscribeTo(RECAUDADORA, Money.cop(75_000),
                    SubscriptionId.of("s3"), TransactionId.of("t3"), NOW);

            assertThat(outcome.client().balance()).isEqualTo(Money.cop(425_000));
        }

        @Test
        @DisplayName("rejects cancelling a fund the client is not subscribed to")
        void rejectsUnknownSubscription() {
            final Client client = aClient();

            assertThatThrownBy(() -> client.cancelSubscriptionTo(
                    ACCIONES.id(), TransactionId.of("txn-2"), NOW))
                    .isInstanceOf(SubscriptionNotFoundException.class);
        }
    }
}
