package com.yam.funds.domain.port.in;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import reactor.core.publisher.Mono;

/** Reads a client's available balance and the funds they are currently linked to. */
public interface GetClientBalanceUseCase {

    /**
     * Returns the client with their balance and active subscriptions.
     *
     * @param clientId client to read
     * @return the client aggregate; the web layer is responsible for projecting it onto
     *         a response DTO, so persistence shape never reaches the API
     * @throws ClientNotFoundException if the client does not exist
     */
    Mono<Client> findClientBalance(ClientId clientId);
}
