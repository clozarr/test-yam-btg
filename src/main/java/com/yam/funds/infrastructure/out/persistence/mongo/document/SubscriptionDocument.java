package com.yam.funds.infrastructure.out.persistence.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Stored form of a subscription, embedded inside its owning {@link ClientDocument}.
 *
 * <p>Not a collection of its own: the subscription belongs to the client aggregate, and
 * embedding is what lets a balance debit and its subscription land in a single atomic
 * document write.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDocument {

    private String id;

    private String fundId;

    private String fundName;

    private MoneyDocument linkedAmount;

    private String status;

    private Instant openedAt;

    private Instant cancelledAt;
}
