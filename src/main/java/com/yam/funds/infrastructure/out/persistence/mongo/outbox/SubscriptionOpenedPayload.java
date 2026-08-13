package com.yam.funds.infrastructure.out.persistence.mongo.outbox;

import com.yam.funds.domain.model.event.SubscriptionOpenedEvent;

/**
 * Wire form of {@link SubscriptionOpenedEvent}.
 *
 * <p>A flat structure of plain types, deliberately separate from the domain event: once
 * published, the payload is a contract with consumers, and it must not shift every time
 * an internal value object is refactored.
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
}
