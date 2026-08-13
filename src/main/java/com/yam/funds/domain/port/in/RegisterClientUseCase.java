package com.yam.funds.domain.port.in;

import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.port.in.command.RegisterClientCommand;
import reactor.core.publisher.Mono;

/** Registers a new client on the platform. */
public interface RegisterClientUseCase {

    /**
     * Creates a client holding the opening balance defined by the business rules.
     *
     * <p>The amount is applied by the implementation, never taken from the caller: an
     * API that let a client choose their starting balance would be a way to mint money.
     *
     * @param command the client's details and notification preference
     * @return the registered client
     */
    Mono<Client> registerClient(RegisterClientCommand command);
}
