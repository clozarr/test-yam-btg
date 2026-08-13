package com.yam.funds.infrastructure.out.persistence.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Spring Data repository for the fund catalogue.
 *
 * <p>Derived queries only — no string concatenation reaches the driver, so there is no
 * surface for SQL injection here.
 */
@Repository
public interface FundR2dbcRepository extends ReactiveCrudRepository<FundEntity, String> {

    Flux<FundEntity> findByActiveIsTrueOrderByIdAsc();
}
