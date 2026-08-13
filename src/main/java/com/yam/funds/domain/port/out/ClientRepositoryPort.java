package com.yam.funds.domain.port.out;

import com.yam.funds.domain.exception.ConcurrentAggregateUpdateException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import reactor.core.publisher.Mono;

/** Stores and retrieves the {@link Client} aggregate. */
public interface ClientRepositoryPort {

    /**
     * Loads a client with their balance and active subscriptions.
     *
     * @param clientId client to load
     * @return the client, or an empty {@code Mono} if no such client exists
     */
    Mono<Client> findById(ClientId clientId);

    /**
     * Persists the aggregate as a whole.
     *
     * <p>Balance and subscriptions land in one document write, so the invariant tying
     * them together never has to survive a partial update. The write is guarded by the
     * aggregate's version: a concurrent modification fails rather than overwriting.
     *
     * @param client the aggregate state to store
     * @return the stored client, carrying its new version
     * @throws ConcurrentAggregateUpdateException if the aggregate changed since it was read
     */
    Mono<Client> save(Client client);

    /**
     * Checks whether an address is already taken, so registration can reject duplicates.
     *
     * @param email address to look for
     * @return {@code true} if a client is already registered with this address
     */
    Mono<Boolean> existsByEmail(String email);
}
