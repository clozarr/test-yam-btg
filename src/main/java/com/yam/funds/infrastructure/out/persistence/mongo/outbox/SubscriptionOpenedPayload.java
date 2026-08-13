package com.yam.funds.infrastructure.out.persistence.mongo.outbox;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Wire form of {@link SubscriptionOpenedEvent}.
 *
 * <p>A flat structure of plain types, deliberately separate from the domain event: once
 * published, the payload is a contract with consumers, and it must not shift every time
 * an internal value object is refactored.
 *
 * <p>Shared by the outbox writer and the Kafka consumer so the schema is declared once.
 * A second definition on the reading side would be free to drift out of step with it.
 */
public record SubscriptionOpenedPayload(
        String eventId,
        String clientId,
        String clientFullName,
        String email,
        String phone,
        String channel,
        String fundId,
        String fundName,
        String amount,
        String currency,
        String transactionId,
        String occurredAt) {

    public static SubscriptionOpenedPayload from(final SubscriptionOpenedEvent event) {
        return new SubscriptionOpenedPayload(
                event.eventId(),
                event.clientId().value(),
                event.clientFullName(),
                event.email(),
                event.phone(),
                event.channel().name(),
                event.fundId().value(),
                event.fundName(),
                event.amount().amount().toPlainString(),
                event.amount().currency().getCurrencyCode(),
                event.transactionId().value(),
                event.occurredAt().toString());
    }

    /** Rebuilds the domain event on the consuming side. */
    public SubscriptionOpenedEvent toEvent() {
        return new SubscriptionOpenedEvent(
                eventId,
                ClientId.of(clientId),
                clientFullName,
                email,
                phone,
                NotificationChannel.valueOf(channel),
                FundId.of(fundId),
                fundName,
                Money.of(new BigDecimal(amount), Currency.getInstance(currency)),
                TransactionId.of(transactionId),
                Instant.parse(occurredAt));
    }
}
