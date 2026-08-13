package com.yam.funds.infrastructure.in.web.controller;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.TransactionCursor;
import com.yam.funds.domain.port.in.GetTransactionHistoryUseCase;
import com.yam.funds.domain.port.in.query.TransactionHistoryQuery;
import com.yam.funds.infrastructure.in.web.TransactionCursorCodec;
import com.yam.funds.infrastructure.in.web.dto.TransactionPageResponse;
import com.yam.funds.infrastructure.in.web.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Transactions", description = "History of subscription openings and cancellations")
@SecurityRequirement(name = "bearer-jwt")
@Validated
@RestController
@RequestMapping(value = "/api/v1/clients/{clientId}/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TransactionController {

    private final GetTransactionHistoryUseCase getTransactionHistoryUseCase;

    @Operation(summary = "Read a page of a client's transaction history, newest first")
    @PreAuthorize("#clientId == authentication.name or hasRole('ADMIN')")
    @GetMapping
    public Mono<TransactionPageResponse> getHistory(
            @PathVariable final String clientId,
            @Parameter(description = "Opaque token from the previous page's nextCursor")
            @RequestParam(required = false) final String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must not exceed 100")
            final int limit) {

        final TransactionHistoryQuery query = new TransactionHistoryQuery(
                ClientId.of(clientId), TransactionCursorCodec.decode(cursor), limit);

        return getTransactionHistoryUseCase.findTransactionHistory(query)
                .collectList()
                .map(transactions -> toPage(transactions, limit));
    }

    /**
     * The next cursor is only offered when the page came back full. A short page means
     * the ledger is exhausted, and handing out a cursor there would invite a round trip
     * that can only return nothing.
     */
    private TransactionPageResponse toPage(
            final List<com.yam.funds.domain.model.FundTransaction> transactions, final int limit) {

        final List<TransactionResponse> items = transactions.stream()
                .map(TransactionResponse::from)
                .toList();

        final String nextCursor = transactions.size() < limit
                ? null
                : TransactionCursorCodec.encode(
                        TransactionCursor.of(transactions.get(transactions.size() - 1)));

        return new TransactionPageResponse(items, nextCursor);
    }
}
