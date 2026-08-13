package com.yam.funds.infrastructure.out.persistence.mongo;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.IdempotencyOperation;
import com.yam.funds.domain.model.IdempotencyRecord;
import com.yam.funds.domain.model.IdempotencyStatus;
import com.yam.funds.domain.port.out.IdempotencyPort;
import com.yam.funds.domain.port.out.IdempotencyReservation;
import com.yam.funds.infrastructure.out.persistence.mongo.document.IdempotencyDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoIdempotencyAdapter implements IdempotencyPort {

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_LEASE_EXPIRES_AT = "leaseExpiresAt";
    private static final String FIELD_RESPONSE_PAYLOAD = "responsePayload";
    private static final String FIELD_REQUEST_FINGERPRINT = "requestFingerprint";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_EXPIRES_AT = "expiresAt";

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Claims the key by inserting it. The document id is the scoped key, so a second
     * insert is rejected by the primary key index — the database, not the application,
     * decides who wins the race.
     */
    @Override
    public Mono<IdempotencyReservation> reserve(final IdempotencyRecord candidate) {
        return mongoTemplate.insert(toDocument(candidate))
                .map(inserted -> (IdempotencyReservation) new IdempotencyReservation.Acquired(candidate))
                .onErrorResume(DuplicateKeyException.class, duplicate -> loadExisting(candidate));
    }

    @Override
    public Mono<Void> complete(final IdempotencyRecord record) {
        final Update update = new Update()
                .set(FIELD_STATUS, record.status().name())
                .set(FIELD_RESPONSE_PAYLOAD, record.responsePayload())
                .set(FIELD_LEASE_EXPIRES_AT, record.leaseExpiresAt());

        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(record.key())),
                        update,
                        IdempotencyDocument.class)
                .then();
    }

    /**
     * Removes the reservation, but only while it is still ours: the match requires the
     * record to be in progress and to carry our own creation timestamp, so a reservation
     * another instance reclaimed after our lease expired is left alone.
     */
    @Override
    public Mono<Void> release(final IdempotencyRecord reservation) {
        final Query query = Query.query(Criteria.where("_id").is(reservation.key())
                .and(FIELD_STATUS).is(IdempotencyStatus.IN_PROGRESS.name())
                .and(FIELD_CREATED_AT).is(reservation.createdAt()));

        return mongoTemplate.remove(query, IdempotencyDocument.class)
                .doOnNext(result -> log.debug("[release] freed {} reservation(s) for key {}",
                        result.getDeletedCount(), reservation.key()))
                .then();
    }

    /**
     * Takes over a reservation left behind by a crashed instance.
     *
     * <p>The conditional update is the whole point: it only matches while the record is
     * still in progress and its lease is still expired, so exactly one of several
     * instances attempting the same recovery succeeds.
     */
    @Override
    public Mono<Boolean> reclaimExpired(final IdempotencyRecord candidate) {
        final Query query = Query.query(Criteria.where("_id").is(candidate.key())
                .and(FIELD_STATUS).is(IdempotencyStatus.IN_PROGRESS.name())
                .and(FIELD_LEASE_EXPIRES_AT).lt(candidate.createdAt()));

        final Update update = new Update()
                .set(FIELD_LEASE_EXPIRES_AT, candidate.leaseExpiresAt())
                .set(FIELD_EXPIRES_AT, candidate.expiresAt())
                .set(FIELD_REQUEST_FINGERPRINT, candidate.requestFingerprint())
                .set(FIELD_CREATED_AT, candidate.createdAt());

        return mongoTemplate.updateFirst(query, update, IdempotencyDocument.class)
                .map(result -> result.getModifiedCount() > 0)
                .doOnNext(reclaimed -> {
                    if (Boolean.TRUE.equals(reclaimed)) {
                        log.warn("[reclaimExpired] took over abandoned reservation {}", candidate.key());
                    }
                });
    }

    private Mono<IdempotencyReservation> loadExisting(final IdempotencyRecord candidate) {
        return mongoTemplate.findById(candidate.key(), IdempotencyDocument.class)
                .map(existing -> (IdempotencyReservation)
                        new IdempotencyReservation.AlreadyHeld(toDomain(existing)))
                // The record vanished between the failed insert and this read, which the
                // TTL index can do. Retrying the reservation is the correct response.
                .switchIfEmpty(Mono.defer(() -> reserve(candidate)));
    }

    private static IdempotencyDocument toDocument(final IdempotencyRecord record) {
        return IdempotencyDocument.builder()
                .id(record.key())
                .clientId(record.clientId().value())
                .operation(record.operation().name())
                .requestFingerprint(record.requestFingerprint())
                .status(record.status().name())
                .responsePayload(record.responsePayload())
                .createdAt(record.createdAt())
                .leaseExpiresAt(record.leaseExpiresAt())
                .expiresAt(record.expiresAt())
                .build();
    }

    private static IdempotencyRecord toDomain(final IdempotencyDocument document) {
        return new IdempotencyRecord(
                document.getId(),
                ClientId.of(document.getClientId()),
                IdempotencyOperation.valueOf(document.getOperation()),
                document.getRequestFingerprint(),
                IdempotencyStatus.valueOf(document.getStatus()),
                document.getResponsePayload(),
                document.getCreatedAt(),
                document.getLeaseExpiresAt(),
                document.getExpiresAt());
    }
}
