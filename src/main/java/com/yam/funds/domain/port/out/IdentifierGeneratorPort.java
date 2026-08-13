package com.yam.funds.domain.port.out;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.SubscriptionId;
import com.yam.funds.domain.model.TransactionId;

/**
 * Supplies the identifiers assigned to newly created aggregates.
 *
 * <p>A port rather than a direct call to a random generator so use case tests can pin
 * the identifiers and assert on exact values — for a financial ledger, "some UUID was
 * produced" is a weaker assertion than the tests deserve.
 */
public interface IdentifierGeneratorPort {

    ClientId nextClientId();

    SubscriptionId nextSubscriptionId();

    TransactionId nextTransactionId();
}
