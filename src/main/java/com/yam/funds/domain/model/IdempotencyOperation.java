package com.yam.funds.domain.model;

/**
 * Operation an idempotency key applies to.
 *
 * <p>Part of the key's scope, so the same client-supplied key used on two different
 * operations never collides.
 */
public enum IdempotencyOperation {

    SUBSCRIBE,

    CANCEL
}
