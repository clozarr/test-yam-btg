package com.yam.funds.infrastructure.out.persistence.mongo;

import com.yam.funds.domain.exception.ConcurrentAggregateUpdateException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.infrastructure.out.persistence.mongo.document.ClientDocument;
import com.yam.funds.infrastructure.out.persistence.mongo.mapper.ClientMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoClientRepositoryAdapter implements ClientRepositoryPort {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Client> findById(final ClientId clientId) {
        return mongoTemplate.findById(clientId.value(), ClientDocument.class)
                .map(ClientMapper::toDomain);
    }

    @Override
    public Mono<Client> save(final Client client) {
        return mongoTemplate.save(ClientMapper.toDocument(client))
                .map(ClientMapper::toDomain)
                // Translated at the boundary so the domain and the use cases never have
                // to know which persistence technology raised it.
                .onErrorMap(OptimisticLockingFailureException.class,
                        error -> new ConcurrentAggregateUpdateException(client.id()));
    }

    @Override
    public Mono<Boolean> existsByEmail(final String email) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("email").is(email)), ClientDocument.class);
    }
}
