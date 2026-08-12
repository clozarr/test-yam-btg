package com.yam.funds.domain.model;

/** Kind of movement recorded in the transaction ledger. */
public enum TransactionType {

    /** Client subscribed to a fund; the linked amount was debited from the balance. */
    OPENING,

    /** Client cancelled a subscription; the linked amount was returned to the balance. */
    CANCELLATION
}
