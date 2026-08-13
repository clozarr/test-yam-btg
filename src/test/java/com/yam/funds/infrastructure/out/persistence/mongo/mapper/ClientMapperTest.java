package com.yam.funds.infrastructure.out.persistence.mongo.mapper;

import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.Fund;
import com.yam.funds.domain.model.FundCategory;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.SubscriptionStatus;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.infrastructure.out.persistence.mongo.document.ClientDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMapperTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");

    private static final Fund RECAUDADORA = new Fund(
            FundId.of("1"), "FPV_AM_PACTUAL_RECAUDADORA", Money.cop(75_000), FundCategory.FPV, true);

    private static Client aClient() {
        return Client.register(
                ClientId.of("client-1"), "Ada Lovelace", "ada@example.com", "+573001112233",
                NotificationChannel.EMAIL, Money.cop(500_000));
    }

    @Test
    @DisplayName("round-trips a client with no subscriptions")
    void roundTripsPlainClient() {
        final Client original = aClient();

        final Client restored = ClientMapper.toDomain(ClientMapper.toDocument(original));

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("round-trips a client holding an active subscription")
    void roundTripsSubscribedClient() {
        final Client original = aClient()
                .subscribeTo(RECAUDADORA, Money.cop(75_000),
                        SubscriptionId.of("sub-1"), TransactionId.of("txn-1"), NOW)
                .client();

        final Client restored = ClientMapper.toDomain(ClientMapper.toDocument(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.balance()).isEqualTo(Money.cop(425_000));
        assertThat(restored.findActiveSubscription(FundId.of("1"))).isPresent().get()
                .satisfies(subscription -> {
                    assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
                    assertThat(subscription.linkedAmount()).isEqualTo(Money.cop(75_000));
                    assertThat(subscription.fundName()).isEqualTo("FPV_AM_PACTUAL_RECAUDADORA");
                    assertThat(subscription.openedAt()).isEqualTo(NOW);
                });
    }

    @Test
    @DisplayName("keys embedded subscriptions by fund id")
    void keysSubscriptionsByFund() {
        final Client subscribed = aClient()
                .subscribeTo(RECAUDADORA, Money.cop(75_000),
                        SubscriptionId.of("sub-1"), TransactionId.of("txn-1"), NOW)
                .client();

        final ClientDocument document = ClientMapper.toDocument(subscribed);

        assertThat(document.getActiveSubscriptions()).containsOnlyKeys("1");
    }

    @Test
    @DisplayName("stores the amount as an exact string, not a floating point value")
    void storesAmountExactly() {
        final ClientDocument document = ClientMapper.toDocument(aClient());

        assertThat(document.getBalance().getAmount()).isEqualTo("500000.00");
        assertThat(document.getBalance().getCurrency()).isEqualTo("COP");
    }

    @Test
    @DisplayName("preserves an amount with decimals through the round trip")
    void preservesDecimals() {
        final Client withDecimals = Client.register(
                ClientId.of("client-2"), "Grace Hopper", "grace@example.com", "+573004445566",
                NotificationChannel.SMS, Money.cop(new BigDecimal("123456.78")));

        final Client restored = ClientMapper.toDomain(ClientMapper.toDocument(withDecimals));

        assertThat(restored.balance().amount()).isEqualByComparingTo(new BigDecimal("123456.78"));
    }

    @Test
    @DisplayName("carries the version so optimistic locking keeps working across the mapping")
    void carriesVersion() {
        final ClientDocument document = ClientMapper.toDocument(aClient());
        document.setVersion(7L);

        assertThat(ClientMapper.toDomain(document).version()).isEqualTo(7L);
    }

    @Test
    @DisplayName("round-trips a client registered without an email address")
    void roundTripsWithoutEmail() {
        final Client smsOnly = Client.register(
                ClientId.of("client-3"), "Alan Turing", null, "+573007778899",
                NotificationChannel.SMS, Money.cop(500_000));

        final Client restored = ClientMapper.toDomain(ClientMapper.toDocument(smsOnly));

        assertThat(restored.email()).isNull();
        assertThat(restored).isEqualTo(smsOnly);
    }
}
