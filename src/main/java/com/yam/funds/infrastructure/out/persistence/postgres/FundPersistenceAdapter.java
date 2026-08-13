package com.yam.funds.infrastructure.out.persistence.postgres;

import com.yam.funds.domain.model.Fund;
import com.yam.funds.domain.model.FundCategory;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.port.out.FundRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Currency;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FundPersistenceAdapter implements FundRepositoryPort {

    private final FundR2dbcRepository fundRepository;

    @Override
    public Mono<Fund> findById(final FundId fundId) {
        return fundRepository.findById(fundId.value()).map(FundPersistenceAdapter::toDomain);
    }

    @Override
    public Flux<Fund> findAllActive() {
        return fundRepository.findByActiveIsTrueOrderByIdAsc().map(FundPersistenceAdapter::toDomain);
    }

    private static Fund toDomain(final FundEntity entity) {
        return new Fund(
                FundId.of(entity.getId()),
                entity.getName(),
                Money.of(entity.getMinimumAmount(), Currency.getInstance(entity.getCurrency())),
                FundCategory.valueOf(entity.getCategory()),
                entity.isActive());
    }
}
