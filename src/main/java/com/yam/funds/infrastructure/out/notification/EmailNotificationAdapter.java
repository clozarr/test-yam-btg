package com.yam.funds.infrastructure.out.notification;

import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import com.yam.funds.domain.port.out.NotificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Simulated email delivery.
 *
 * <p>Logs the notice instead of contacting a provider. Swapping in a real transport
 * (SES, SendGrid) means replacing this class — nothing above the port changes, which is
 * the point of having the port at all.
 */
@Slf4j
@Component
public class EmailNotificationAdapter implements NotificationSenderPort {

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public Mono<Void> send(final SubscriptionOpenedEvent event) {
        return Mono.fromRunnable(() -> log.info(
                "[send] [EMAIL] to={} subject='Subscription confirmed' body='{}, your subscription to "
                        + "{} for {} was confirmed. Transaction {}.'",
                maskEmail(event.email()),
                event.clientFullName(),
                event.fundName(),
                event.amount(),
                event.transactionId()));
    }

    /**
     * Addresses are personal data and logs are widely readable, so only enough is kept
     * to correlate a delivery with a complaint.
     */
    private static String maskEmail(final String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        final int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at < 0 ? "" : email.substring(at));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
