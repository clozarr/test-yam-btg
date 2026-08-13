package com.yam.funds.application.usecase;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import com.yam.funds.domain.port.out.NotificationSenderPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NotifySubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");

    /** Records what it was asked to send, so routing can be asserted without mocks. */
    private static final class RecordingSender implements NotificationSenderPort {

        private final NotificationChannel channel;
        private final AtomicReference<SubscriptionOpenedEvent> received = new AtomicReference<>();

        private RecordingSender(final NotificationChannel channel) {
            this.channel = channel;
        }

        @Override
        public NotificationChannel supportedChannel() {
            return channel;
        }

        @Override
        public Mono<Void> send(final SubscriptionOpenedEvent event) {
            received.set(event);
            return Mono.empty();
        }
    }

    private static SubscriptionOpenedEvent eventFor(final NotificationChannel channel) {
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

    @Test
    @DisplayName("routes the notice to the sender for the client's chosen channel")
    void routesByChannel() {
        final RecordingSender email = new RecordingSender(NotificationChannel.EMAIL);
        final RecordingSender sms = new RecordingSender(NotificationChannel.SMS);
        final NotifySubscriptionService service = new NotifySubscriptionService(List.of(email, sms));

        StepVerifier.create(service.notifySubscription(eventFor(NotificationChannel.SMS)))
                .verifyComplete();

        assertThat(sms.received.get()).isNotNull();
        assertThat(email.received.get()).isNull();
    }

    @Test
    @DisplayName("hands the sender the contact details carried by the event")
    void passesContactDetails() {
        final RecordingSender email = new RecordingSender(NotificationChannel.EMAIL);
        final NotifySubscriptionService service = new NotifySubscriptionService(List.of(email));

        service.notifySubscription(eventFor(NotificationChannel.EMAIL)).block();

        assertThat(email.received.get().email()).isEqualTo("ada@example.com");
        assertThat(email.received.get().fundName()).isEqualTo("FPV_AM_PACTUAL_RECAUDADORA");
    }

    @Test
    @DisplayName("fails loudly when no sender covers the requested channel")
    void failsOnUnsupportedChannel() {
        final NotifySubscriptionService service = new NotifySubscriptionService(
                List.of(new RecordingSender(NotificationChannel.EMAIL)));

        StepVerifier.create(service.notifySubscription(eventFor(NotificationChannel.SMS)))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
