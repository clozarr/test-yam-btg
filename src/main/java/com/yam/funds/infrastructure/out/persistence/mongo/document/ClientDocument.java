package com.yam.funds.infrastructure.out.persistence.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Stored form of the client aggregate.
 *
 * <p>Active subscriptions are embedded and keyed by fund id, so the balance and the
 * subscriptions it must stay consistent with are updated in one document write. Only
 * active ones are kept, which bounds the document by the size of the fund catalogue;
 * cancelled ones live on in the transaction ledger.
 *
 * <p>{@code version} drives optimistic locking: two concurrent subscriptions for the
 * same client cannot both succeed on a stale balance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = ClientDocument.COLLECTION)
public class ClientDocument {

    public static final String COLLECTION = "clients";

    @Id
    private String id;

    private String fullName;

    private String email;

    private String phone;

    private String notificationPreference;

    private MoneyDocument balance;

    private Map<String, SubscriptionDocument> activeSubscriptions;

    @Version
    private Long version;
}
