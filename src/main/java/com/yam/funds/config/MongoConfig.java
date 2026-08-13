package com.yam.funds.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Wiring for MongoDB transactions.
 *
 * <p>{@code @Transactional} is not usable here: on a reactive pipeline it would commit
 * when the method returned its publisher, before any work had been subscribed to.
 * {@link TransactionalOperator} binds the transaction to the reactive context instead.
 */
@Configuration
public class MongoConfig {

    @Bean
    public ReactiveMongoTransactionManager reactiveMongoTransactionManager(
            final ReactiveMongoDatabaseFactory databaseFactory) {
        return new ReactiveMongoTransactionManager(databaseFactory);
    }

    @Bean
    public TransactionalOperator transactionalOperator(
            final ReactiveMongoTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
