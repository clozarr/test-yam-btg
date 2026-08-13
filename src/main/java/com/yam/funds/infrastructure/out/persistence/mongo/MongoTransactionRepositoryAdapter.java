package com.yam.funds.infrastructure.out.persistence.mongo;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.TransactionCursor;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import com.yam.funds.infrastructure.out.persistence.mongo.document.FundTransactionDocument;
import com.yam.funds.infrastructure.out.persistence.mongo.mapper.FundTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoTransactionRepositoryAdapter implements TransactionRepositoryPort {

    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_OCCURRED_AT = "occurredAt";
    private static final String FIELD_ID = "_id";

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<FundTransaction> save(final FundTransaction transaction) {
        // insert, not save: the ledger is append-only, so an id collision must fail
        // loudly rather than silently overwrite an existing entry.
        return mongoTemplate.insert(FundTransactionMapper.toDocument(transaction))
                .map(FundTransactionMapper::toDomain);
    }

    @Override
    public Mono<FundTransaction> findById(final TransactionId transactionId) {
        return mongoTemplate.findById(transactionId.value(), FundTransactionDocument.class)
                .map(FundTransactionMapper::toDomain);
    }

    @Override
    public Flux<FundTransaction> findByClient(
            final ClientId clientId, final TransactionCursor cursor, final int limit) {

        final Query query = Query.query(pageCriteria(clientId, cursor))
                .with(Sort.by(Sort.Direction.DESC, FIELD_OCCURRED_AT, FIELD_ID))
                .limit(limit);

        return mongoTemplate.find(query, FundTransactionDocument.class)
                .map(FundTransactionMapper::toDomain);
    }

    /**
     * Keyset predicate for "strictly older than the cursor".
     *
     * <p>Compares the id as a tiebreaker when two entries share an instant; comparing
     * the timestamp alone would drop or repeat entries that happen to be simultaneous.
     */
    private Criteria pageCriteria(final ClientId clientId, final TransactionCursor cursor) {
        final Criteria base = Criteria.where(FIELD_CLIENT_ID).is(clientId.value());
        if (cursor == null) {
            return base;
        }
        return base.andOperator(new Criteria().orOperator(
                Criteria.where(FIELD_OCCURRED_AT).lt(cursor.occurredAt()),
                Criteria.where(FIELD_OCCURRED_AT).is(cursor.occurredAt())
                        .and(FIELD_ID).lt(cursor.transactionId().value())));
    }
}
