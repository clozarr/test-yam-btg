package com.yam.funds.infrastructure.out.notification;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");

    private final EmailNotificationAdapter emailAdapter = new EmailNotificationAdapter();
    private final SmsNotificationAdapter smsAdapter = new SmsNotificationAdapter();

    private static SubscriptionOpenedEvent anEvent(final String email, final String phone) {
        return new SubscriptionOpenedEvent(
                "event-1", ClientId.of("client-1"), "Ada Lovelace", email, phone,
                NotificationChannel.EMAIL, FundId.of("1"), "FPV_AM_PACTUAL_RECAUDADORA",
                Money.cop(75_000), TransactionId.of("txn-1"), NOW);
    }

    @Test
    @DisplayName("each adapter declares the single channel it serves")
    void declaresItsChannel() {
        assertThat(emailAdapter.supportedChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(smsAdapter.supportedChannel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    @DisplayName("completes without emitting a value")
    void completesEmpty() {
        StepVerifier.create(emailAdapter.send(anEvent("ada@example.com", "+573001112233")))
                .verifyComplete();
        StepVerifier.create(smsAdapter.send(anEvent("ada@example.com", "+573001112233")))
                .verifyComplete();
    }

    @Test
    @DisplayName("tolerates a client with no address for the other channel")
    void toleratesMissingContact() {
        StepVerifier.create(emailAdapter.send(anEvent(null, "+573001112233"))).verifyComplete();
        StepVerifier.create(smsAdapter.send(anEvent("ada@example.com", null))).verifyComplete();
    }
}
