package com.yam.funds.application.usecase;

import com.yam.funds.domain.exception.ClientNotFoundException;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.port.in.GetTransactionHistoryUseCase;
import com.yam.funds.domain.port.in.query.TransactionHistoryQuery;
import com.yam.funds.domain.port.out.ClientRepositoryPort;
import com.yam.funds.domain.port.out.TransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetTransactionHistoryService implements GetTransactionHistoryUseCase {

    private final ClientRepositoryPort clientRepository;
    private final TransactionRepositoryPort transactionRepository;

    @Override
    public Flux<FundTransaction> findTransactionHistory(final TransactionHistoryQuery query) {
        return clientRepository.findById(query.clientId())
                .switchIfEmpty(Mono.error(() -> new ClientNotFoundException(query.clientId())))
                // An unknown client is reported as such rather than as an empty history,
                // which would be indistinguishable from a client who never transacted.
                // Deferred so the ledger is only queried once the client is known to
                // exist: thenMany would otherwise assemble the query eagerly.
                .thenMany(Flux.defer(() -> transactionRepository.findByClient(
                        query.clientId(), query.cursor(), query.limit())))
                .doFirst(() -> log.info("[findTransactionHistory] [BEGIN] clientId={} limit={} cursor={}",
                        query.clientId(), query.limit(), query.cursor()))
                .doOnComplete(() -> log.info("[findTransactionHistory] [END OK] clientId={}", query.clientId()))
                .doOnError(error -> {
                    log.error("[findTransactionHistory] [END EX] clientId={}. Details: {}",
                            query.clientId(), error.getMessage());
                    log.warn(error.getLocalizedMessage(), error);
                });
    }
}
