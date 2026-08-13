package com.yam.funds.infrastructure.out.persistence.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Stored form of an idempotency reservation.
 *
 * <p>The scoped key is the document {@code _id}, so MongoDB's unique primary key is
 * what rejects a duplicate insert. That rejection — not a preceding existence check —
 * is what serialises two requests arriving with the same key at the same instant.
 *
 * <p>{@code expiresAt} backs a TTL index, so completed reservations are reaped without
 * an explicit cleanup job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = IdempotencyDocument.COLLECTION)
public class IdempotencyDocument {

    public static final String COLLECTION = "idempotency_records";

    @Id
    private String id;

    private String clientId;

    private String operation;

    private String requestFingerprint;

    private String status;

    private String responsePayload;

    private Instant createdAt;

    private Instant leaseExpiresAt;

    private Instant expiresAt;
}
