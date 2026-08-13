package com.yam.funds.application.usecase;

import com.yam.funds.domain.model.Fund;
import com.yam.funds.domain.port.in.ListFundsUseCase;
import com.yam.funds.domain.port.out.FundRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListFundsService implements ListFundsUseCase {

    private final FundRepositoryPort fundRepository;

    @Override
    public Flux<Fund> findAvailableFunds() {
        return fundRepository.findAllActive()
                .doFirst(() -> log.info("[findAvailableFunds] [BEGIN] reading the active fund catalogue"))
                .doOnComplete(() -> log.info("[findAvailableFunds] [END OK] catalogue read"))
                .doOnError(error -> {
                    log.error("[findAvailableFunds] [END EX] Details: {}", error.getMessage());
                    log.warn(error.getLocalizedMessage(), error);
                });
    }
}
