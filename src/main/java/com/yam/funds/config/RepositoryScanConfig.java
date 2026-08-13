package com.yam.funds.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Confines R2DBC repository scanning to the PostgreSQL adapter package.
 *
 * <p>With both MongoDB and R2DBC on the classpath, Spring Data has to guess which store
 * an unannotated repository interface belongs to, and it warned about doing so for the
 * fund catalogue. Naming the package removes the guesswork, and stops a future MongoDB
 * repository from being handed to the wrong store.
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.yam.funds.infrastructure.out.persistence.postgres")
public class RepositoryScanConfig {
}
