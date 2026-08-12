package com.yam.funds.domain.model.event;

import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.Subscription;
import com.yam.funds.domain.model.TransactionId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a client subscribes to a fund.
 *
 * <p>Carries the contact details and chosen channel so the notification consumer can
 * dispatch without reading the client back — the notification is then a pure function
 * of the event, and cannot observe a client that changed in the meantime.
 */
public record SubscriptionOpenedEvent(
        String eventId,
        ClientId clientId,
        String clientFullName,
        String email,
        String phone,
        NotificationChannel channel,
        FundId fundId,
        String fundName,
        Money amount,
        TransactionId transactionId,
        Instant occurredAt) {

    public static SubscriptionOpenedEvent from(
            final Client client,
            final Subscription subscription,
            final TransactionId transactionId,
            final Instant occurredAt) {
        return new SubscriptionOpenedEvent(
                UUID.randomUUID().toString(),
                client.id(),
                client.fullName(),
                client.email(),
                client.phone(),
                client.notificationPreference(),
                subscription.fundId(),
                subscription.fundName(),
                subscription.linkedAmount(),
                transactionId,
                occurredAt);
    }
}
