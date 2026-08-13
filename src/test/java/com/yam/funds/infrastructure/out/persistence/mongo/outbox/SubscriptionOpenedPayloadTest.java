package com.yam.funds.infrastructure.out.persistence.mongo.outbox;

import tools.jackson.databind.ObjectMapper;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionOpenedPayloadTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static SubscriptionOpenedEvent anEvent(final NotificationChannel channel) {
        return new SubscriptionOpenedEvent(
                "event-1",
                ClientId.of("client-1"),
                "Ada Lovelace",
                "ada@example.com",
                "+573001112233",
                channel,
                FundId.of("1"),
                "FPV_AM_PACTUAL_RECAUDADORA",
                Money.cop(75_000),
                TransactionId.of("txn-1"),
                NOW);
    }

    @ParameterizedTest
    @EnumSource(NotificationChannel.class)
    @DisplayName("round-trips the event through its wire form")
    void roundTripsInMemory(final NotificationChannel channel) {
        final SubscriptionOpenedEvent original = anEvent(channel);

        assertThat(SubscriptionOpenedPayload.from(original).toEvent()).isEqualTo(original);
    }

    @Test
    @DisplayName("survives serialisation and deserialisation, which is what the broker does to it")
    void roundTripsThroughJson() throws Exception {
        final SubscriptionOpenedEvent original = anEvent(NotificationChannel.EMAIL);

        final String json = objectMapper.writeValueAsString(SubscriptionOpenedPayload.from(original));
        final SubscriptionOpenedEvent restored =
                objectMapper.readValue(json, SubscriptionOpenedPayload.class).toEvent();

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("carries the amount as an exact string, never as a JSON number")
    void carriesAmountAsString() throws Exception {
        final String json = objectMapper.writeValueAsString(
                SubscriptionOpenedPayload.from(anEvent(NotificationChannel.EMAIL)));

        // A JSON number would be read back as a double by a naive consumer and lose
        // precision on an amount; a string forces an exact decimal parse.
        assertThat(json).contains("\"amount\":\"75000.00\"").contains("\"currency\":\"COP\"");
    }

    @Test
    @DisplayName("preserves an amount with decimals")
    void preservesDecimals() {
        final SubscriptionOpenedEvent withDecimals = new SubscriptionOpenedEvent(
                "event-2", ClientId.of("client-1"), "Ada Lovelace", "ada@example.com",
                "+573001112233", NotificationChannel.EMAIL, FundId.of("1"),
                "FPV_AM_PACTUAL_RECAUDADORA", Money.cop(new BigDecimal("123456.78")),
                TransactionId.of("txn-1"), NOW);

        assertThat(SubscriptionOpenedPayload.from(withDecimals).toEvent().amount().amount())
                .isEqualByComparingTo(new BigDecimal("123456.78"));
    }
}
