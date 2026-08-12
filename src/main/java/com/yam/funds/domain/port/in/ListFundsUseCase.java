package com.yam.funds.domain.port.in;

import com.yam.funds.domain.model.Fund;
import reactor.core.publisher.Flux;

/** Reads the catalogue of funds a client can subscribe to. */
public interface ListFundsUseCase {

    /**
     * Returns every fund currently open to new subscriptions.
     *
     * @return the active catalogue; empty if no fund is currently offered
     */
    Flux<Fund> findAvailableFunds();
}
