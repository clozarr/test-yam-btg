package com.yam.funds.infrastructure.out.persistence.mongo.mapper;

import com.yam.funds.domain.model.Money;
import com.yam.funds.infrastructure.out.persistence.mongo.document.MoneyDocument;

import java.math.BigDecimal;
import java.util.Currency;

/** Converts between {@link Money} and its stored form. */
public final class MoneyMapper {

    private MoneyMapper() {
    }

    public static MoneyDocument toDocument(final Money money) {
        if (money == null) {
            return null;
        }
        return MoneyDocument.builder()
                .amount(money.amount().toPlainString())
                .currency(money.currency().getCurrencyCode())
                .build();
    }

    public static Money toDomain(final MoneyDocument document) {
        if (document == null) {
            return null;
        }
        return Money.of(new BigDecimal(document.getAmount()), Currency.getInstance(document.getCurrency()));
    }
}
