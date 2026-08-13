package com.yam.funds.application.usecase;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.model.Client;
import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.port.in.GetClientBalanceUseCase;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetClientBalanceService implements GetClientBalanceUseCase {

    private final ClientRepositoryPort clientRepository;

    @Override
    public Mono<Client> findClientBalance(final ClientId clientId) {
        return clientRepository.findById(clientId)
                .switchIfEmpty(Mono.error(() -> new ClientNotFoundException(clientId)))
                .doFirst(() -> log.info("[findClientBalance] [BEGIN] clientId={}", clientId))
                .doOnSuccess(client -> log.info("[findClientBalance] [END OK] clientId={} balance={}",
                        clientId, client.balance()))
                .doOnError(error -> {
                    log.error("[findClientBalance] [END EX] clientId={}. Details: {}",
                            clientId, error.getMessage());
                    log.warn(error.getLocalizedMessage(), error);
                });
    }
}
