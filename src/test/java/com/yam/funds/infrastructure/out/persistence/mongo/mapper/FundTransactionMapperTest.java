package com.yam.funds.infrastructure.out.persistence.mongo.mapper;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.FundTransaction;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.TransactionId;
import com.yam.funds.domain.model.TransactionType;
import com.yam.funds.infrastructure.out.persistence.mongo.document.FundTransactionDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FundTransactionMapperTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");

    private static FundTransaction aTransaction(final TransactionType type) {
        return new FundTransaction(
                TransactionId.of("txn-1"),
                ClientId.of("client-1"),
                FundId.of("1"),
                "FPV_AM_PACTUAL_RECAUDADORA",
                SubscriptionId.of("sub-1"),
                type,
                Money.cop(75_000),
                Money.cop(425_000),
                NOW);
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    @DisplayName("round-trips every kind of ledger entry")
    void roundTripsEveryType(final TransactionType type) {
        final FundTransaction original = aTransaction(type);

        assertThat(FundTransactionMapper.toDomain(FundTransactionMapper.toDocument(original)))
                .isEqualTo(original);
    }

    @Test
    @DisplayName("maps the transaction id onto the document id, so the ledger cannot hold duplicates")
    void mapsIdOntoDocumentId() {
        final FundTransactionDocument document =
                FundTransactionMapper.toDocument(aTransaction(TransactionType.OPENING));

        assertThat(document.getId()).isEqualTo("txn-1");
    }

    @Test
    @DisplayName("stores both amounts exactly")
    void storesAmountsExactly() {
        final FundTransactionDocument document =
                FundTransactionMapper.toDocument(aTransaction(TransactionType.OPENING));

        assertThat(document.getAmount().getAmount()).isEqualTo("75000.00");
        assertThat(document.getBalanceAfter().getAmount()).isEqualTo("425000.00");
    }

    @Test
    @DisplayName("tolerates a ledger entry with no subscription reference")
    void toleratesMissingSubscription() {
        final FundTransaction withoutSubscription = new FundTransaction(
                TransactionId.of("txn-2"), ClientId.of("client-1"), FundId.of("1"),
                "FPV_AM_PACTUAL_RECAUDADORA", null, TransactionType.OPENING,
                Money.cop(75_000), Money.cop(425_000), NOW);

        final FundTransaction restored = FundTransactionMapper.toDomain(
                FundTransactionMapper.toDocument(withoutSubscription));

        assertThat(restored.subscriptionId()).isNull();
        assertThat(restored).isEqualTo(withoutSubscription);
    }
}
