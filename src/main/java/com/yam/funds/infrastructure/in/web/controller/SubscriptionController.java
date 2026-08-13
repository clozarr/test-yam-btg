package com.yam.funds.infrastructure.in.web.controller;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.model.FundId;
import com.yam.funds.domain.model.Money;
import com.yam.funds.domain.port.in.CancelFundSubscriptionUseCase;
import com.yam.funds.domain.port.in.SubscribeToFundUseCase;
import com.yam.funds.domain.port.in.command.CancelFundSubscriptionCommand;
import com.yam.funds.domain.port.in.command.SubscribeToFundCommand;
import com.yam.funds.infrastructure.in.web.dto.SubscribeToFundRequest;
import com.yam.funds.infrastructure.in.web.dto.SubscriptionReceiptResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Currency;

@Tag(name = "Subscriptions", description = "Linking a client to a fund and unlinking them")
@SecurityRequirement(name = "bearer-jwt")
@Validated
@RestController
@RequestMapping(value = "/api/v1/clients/{clientId}/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SubscriptionController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SubscribeToFundUseCase subscribeToFundUseCase;
    private final CancelFundSubscriptionUseCase cancelFundSubscriptionUseCase;
    private final Currency currency;

    @Operation(summary = "Subscribe a client to a fund",
            description = "Requires an Idempotency-Key header. Repeating a request with the same "
                    + "key returns the original result instead of moving money twice.")
    @PreAuthorize("#clientId == authentication.name or hasRole('ADMIN')")
    @PostMapping(path = "/{fundId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SubscriptionReceiptResponse> subscribe(
            @PathVariable final String clientId,
            @PathVariable final String fundId,
            @Parameter(description = "Unique key making the operation replay-safe", required = true)
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
            @NotBlank(message = "Idempotency-Key is required")
            @Size(min = 8, max = 255, message = "Idempotency-Key must be 8 to 255 characters")
            final String idempotencyKey,
            @Valid @RequestBody final SubscribeToFundRequest request) {

        final SubscribeToFundCommand command = new SubscribeToFundCommand(
                ClientId.of(clientId),
                FundId.of(fundId),
                Money.of(request.amount(), currency),
                idempotencyKey);

        return subscribeToFundUseCase.subscribeToFund(command)
                .map(SubscriptionReceiptResponse::from);
    }

    @Operation(summary = "Cancel a client's subscription, returning the linked amount to their balance")
    @PreAuthorize("#clientId == authentication.name or hasRole('ADMIN')")
    @DeleteMapping("/{fundId}")
    public Mono<SubscriptionReceiptResponse> cancel(
            @PathVariable final String clientId,
            @PathVariable final String fundId,
            @Parameter(description = "Unique key making the operation replay-safe", required = true)
            @RequestHeader(IDEMPOTENCY_KEY_HEADER)
            @NotBlank(message = "Idempotency-Key is required")
            @Size(min = 8, max = 255, message = "Idempotency-Key must be 8 to 255 characters")
            final String idempotencyKey) {

        final CancelFundSubscriptionCommand command = new CancelFundSubscriptionCommand(
                ClientId.of(clientId), FundId.of(fundId), idempotencyKey);

        return cancelFundSubscriptionUseCase.cancelFundSubscription(command)
                .map(SubscriptionReceiptResponse::from);
    }
}
