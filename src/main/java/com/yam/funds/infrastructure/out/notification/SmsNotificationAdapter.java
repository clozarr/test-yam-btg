package com.yam.funds.infrastructure.out.notification;

import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;
import com.yam.funds.domain.port.out.NotificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Simulated SMS delivery.
 *
 * <p>Logs the notice instead of contacting a provider. Swapping in a real transport
 * (SNS, Twilio) means replacing this class only.
 */
@Slf4j
@Component
public class SmsNotificationAdapter implements NotificationSenderPort {

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public Mono<Void> send(final SubscriptionOpenedEvent event) {
        return Mono.fromRunnable(() -> log.info(
                "[send] [SMS] to={} body='{}, your subscription to {} for {} was confirmed. "
                        + "Transaction {}.'",
                maskPhone(event.phone()),
                event.clientFullName(),
                event.fundName(),
                event.amount(),
                event.transactionId()));
    }

    /** Keeps only the last four digits: enough to trace a delivery, not to identify a person. */
    private static String maskPhone(final String phone) {
        if (phone == null || phone.isBlank()) {
            return "<none>";
        }
        return phone.length() <= 4 ? "***" : "***" + phone.substring(phone.length() - 4);
    }
}
