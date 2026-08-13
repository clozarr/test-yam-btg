package com.yam.funds.infrastructure.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A page of ledger entries.
 *
 * @param items      the entries, newest first
 * @param nextCursor opaque token to pass back for the following page; {@code null} once
 *                   the history is exhausted. Opaque on purpose — encoding it keeps
 *                   callers from constructing their own and depending on the internal
 *                   paging key.
 */
@Schema(description = "A page of transaction history")
public record TransactionPageResponse(List<TransactionResponse> items, String nextCursor) {
}
