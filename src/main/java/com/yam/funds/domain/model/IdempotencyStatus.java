package com.yam.funds.domain.model;

/** State of an {@link IdempotencyRecord}. */
public enum IdempotencyStatus {

    /** Reserved by an in-flight request. */
    IN_PROGRESS,

    /** The operation finished and its response can be replayed. */
    COMPLETED
}
