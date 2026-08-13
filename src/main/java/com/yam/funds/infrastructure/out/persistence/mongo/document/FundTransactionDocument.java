package com.yam.funds.infrastructure.out.persistence.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Stored form of a ledger entry. Append-only: never updated once written. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = FundTransactionDocument.COLLECTION)
public class FundTransactionDocument {

    public static final String COLLECTION = "fund_transactions";

    @Id
    private String id;

    private String clientId;

    private String fundId;

    private String fundName;

    private String subscriptionId;

    private String type;

    private MoneyDocument amount;

    private MoneyDocument balanceAfter;

    private Instant occurredAt;
}
