package com.yam.funds.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;

/**
 * Creates the fund catalogue schema and seeds it on startup.
 *
 * <p>Uses the R2DBC initialiser rather than Flyway or Liquibase, which keeps the
 * dependency list unchanged. Both scripts are written to be idempotent
 * ({@code CREATE TABLE IF NOT EXISTS}, {@code ON CONFLICT DO NOTHING}) because they run
 * on every boot.
 *
 * <p>For a system with a real migration history this would not be enough — schema
 * evolution needs versioned, ordered migrations. It is sufficient here because the
 * catalogue is a single fixed table of reference data.
 */
@Configuration
public class R2dbcInitializerConfig {

    @Bean
    public ConnectionFactoryInitializer connectionFactoryInitializer(final ConnectionFactory connectionFactory) {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/schema.sql"),
                new ClassPathResource("db/data.sql"));

        final ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
