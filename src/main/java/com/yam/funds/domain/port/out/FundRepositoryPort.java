package com.yam.funds.domain.port.out;

import com.yam.funds.domain.model.Fund;
import com.yam.funds.domain.model.FundId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reads the fund catalogue.
 *
 * <p>Read-only by design: fund definitions are master data, seeded from the business
 * catalogue and never mutated by a use case.
 */
public interface FundRepositoryPort {

    /**
     * @param fundId fund to load
     * @return the fund, or an empty {@code Mono} if it is not in the catalogue
     */
    Mono<Fund> findById(FundId fundId);

    /**
     * @return every fund currently open to new subscriptions
     */
    Flux<Fund> findAllActive();
}
