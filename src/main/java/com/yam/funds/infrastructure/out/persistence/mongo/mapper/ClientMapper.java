package com.yam.funds.infrastructure.out.persistence.mongo.mapper;

import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.NotificationChannel;
import com.yam.funds.domain.model.Subscription;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.SubscriptionStatus;
import com.yam.funds.infrastructure.out.persistence.mongo.document.ClientDocument;
import com.yam.funds.infrastructure.out.persistence.mongo.document.SubscriptionDocument;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts between the {@link Client} aggregate and its stored form. */
public final class ClientMapper {

    private ClientMapper() {
    }

    public static ClientDocument toDocument(final Client client) {
        final Map<String, SubscriptionDocument> subscriptions = new LinkedHashMap<>();
        client.activeSubscriptions()
                .forEach((fundId, subscription) -> subscriptions.put(fundId.value(), toDocument(subscription)));

        return ClientDocument.builder()
                .id(client.id().value())
                .fullName(client.fullName())
                .email(client.email())
                .phone(client.phone())
                .notificationPreference(client.notificationPreference().name())
                .balance(MoneyMapper.toDocument(client.balance()))
                .activeSubscriptions(subscriptions)
                .version(client.version())
                .build();
    }

    public static Client toDomain(final ClientDocument document) {
        final Map<FundId, Subscription> subscriptions = new LinkedHashMap<>();
        if (document.getActiveSubscriptions() != null) {
            document.getActiveSubscriptions()
                    .forEach((fundId, subscription) ->
                            subscriptions.put(FundId.of(fundId), toDomain(subscription)));
        }

        return new Client(
                ClientId.of(document.getId()),
                document.getFullName(),
                document.getEmail(),
                document.getPhone(),
                NotificationChannel.valueOf(document.getNotificationPreference()),
                MoneyMapper.toDomain(document.getBalance()),
                subscriptions,
                document.getVersion());
    }

    private static SubscriptionDocument toDocument(final Subscription subscription) {
        return SubscriptionDocument.builder()
                .id(subscription.id().value())
                .fundId(subscription.fundId().value())
                .fundName(subscription.fundName())
                .linkedAmount(MoneyMapper.toDocument(subscription.linkedAmount()))
                .status(subscription.status().name())
                .openedAt(subscription.openedAt())
                .cancelledAt(subscription.cancelledAt())
                .build();
    }

    private static Subscription toDomain(final SubscriptionDocument document) {
        return new Subscription(
                SubscriptionId.of(document.getId()),
                FundId.of(document.getFundId()),
                document.getFundName(),
                MoneyMapper.toDomain(document.getLinkedAmount()),
                SubscriptionStatus.valueOf(document.getStatus()),
                document.getOpenedAt(),
                document.getCancelledAt());
    }
}
