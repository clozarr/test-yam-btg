package com.yam.funds.infrastructure.in.web.dto;

import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * A client's balance and current links to funds.
 *
 * <p>Deliberately not the aggregate: the stored version is internal bookkeeping and
 * never leaves the service.
 */
@Schema(description = "A client's balance and active subscriptions")
public record ClientResponse(
        String id,
        String fullName,
        String email,
        String phone,
        String notificationPreference,
        MoneyResponse balance,
        List<ActiveSubscription> activeSubscriptions) {

    @Schema(description = "A fund the client is currently linked to")
    public record ActiveSubscription(
            String subscriptionId,
            String fundId,
            String fundName,
            MoneyResponse linkedAmount,
            Instant openedAt) {

        private static ActiveSubscription from(final Subscription subscription) {
            return new ActiveSubscription(
                    subscription.id().value(),
                    subscription.fundId().value(),
                    subscription.fundName(),
                    MoneyResponse.from(subscription.linkedAmount()),
                    subscription.openedAt());
        }
    }

    public static ClientResponse from(final Client client) {
        final List<ActiveSubscription> subscriptions = client.activeSubscriptions().values().stream()
                .map(ActiveSubscription::from)
                .sorted(Comparator.comparing(ActiveSubscription::fundId))
                .toList();

        return new ClientResponse(
                client.id().value(),
                client.fullName(),
                client.email(),
                client.phone(),
                client.notificationPreference().name(),
                MoneyResponse.from(client.balance()),
                subscriptions);
    }
}
