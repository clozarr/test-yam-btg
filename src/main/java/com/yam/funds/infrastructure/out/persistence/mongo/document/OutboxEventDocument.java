package com.yam.funds.infrastructure.out.persistence.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Event awaiting publication to the broker.
 *
 * <p>Written inside the same transaction as the state change it describes, so the
 * event and the money movement commit or roll back together. A relay forwards it
 * afterwards, which is what keeps a broker outage from failing a subscription.
 *
 * <p>{@code partitionKey} carries the client id: events for one client must reach the
 * broker on a single partition, or their order is not guaranteed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = OutboxEventDocument.COLLECTION)
public class OutboxEventDocument {

    public static final String COLLECTION = "outbox_events";

    public static final String STATUS_PENDING = "PENDING";

    /** Claimed by a relay instance; released back to PENDING if the send fails. */
    public static final String STATUS_PUBLISHING = "PUBLISHING";

    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    private String id;

    private String aggregateType;

    private String aggregateId;

    private String eventType;

    private String partitionKey;

    private String payload;

    private String status;

    private Instant createdAt;

    private Instant publishedAt;

    private int attempts;
}
