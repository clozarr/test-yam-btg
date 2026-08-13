package com.yam.funds.infrastructure.out.persistence.mongo.mapper;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.TransactionType;
import com.yam.funds.infrastructure.out.persistence.mongo.document.FundTransactionDocument;

/** Converts between {@link FundTransaction} and its stored form. */
public final class FundTransactionMapper {

    private FundTransactionMapper() {
    }

    public static FundTransactionDocument toDocument(final FundTransaction transaction) {
        return FundTransactionDocument.builder()
                .id(transaction.id().value())
                .clientId(transaction.clientId().value())
                .fundId(transaction.fundId().value())
                .fundName(transaction.fundName())
                .subscriptionId(transaction.subscriptionId() == null
                        ? null : transaction.subscriptionId().value())
                .type(transaction.type().name())
                .amount(MoneyMapper.toDocument(transaction.amount()))
                .balanceAfter(MoneyMapper.toDocument(transaction.balanceAfter()))
                .occurredAt(transaction.occurredAt())
                .build();
    }

    public static FundTransaction toDomain(final FundTransactionDocument document) {
        return new FundTransaction(
                TransactionId.of(document.getId()),
                ClientId.of(document.getClientId()),
                FundId.of(document.getFundId()),
                document.getFundName(),
                document.getSubscriptionId() == null
                        ? null : SubscriptionId.of(document.getSubscriptionId()),
                TransactionType.valueOf(document.getType()),
                MoneyMapper.toDomain(document.getAmount()),
                MoneyMapper.toDomain(document.getBalanceAfter()),
                document.getOccurredAt());
    }
}
