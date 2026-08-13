package com.yam.funds.infrastructure.in.web.controller;

import com.yam.funds.domain.model.ClientId;
import com.yam.funds.domain.port.in.GetClientBalanceUseCase;
import com.yam.funds.domain.port.in.RegisterClientUseCase;
import com.yam.funds.domain.port.in.command.RegisterClientCommand;
import com.yam.funds.infrastructure.in.web.dto.ClientResponse;
import com.yam.funds.infrastructure.in.web.dto.RegisterClientRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Clients", description = "Client registration and balance")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping(value = "/api/v1/clients", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ClientController {

    private final RegisterClientUseCase registerClientUseCase;
    private final GetClientBalanceUseCase getClientBalanceUseCase;

    /**
     * Restricted to administrators: a client cannot present a token for an account that
     * does not exist yet, so this endpoint cannot be secured by ownership.
     */
    @Operation(summary = "Register a client with the opening balance defined by the business rules")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ClientResponse> registerClient(@Valid @RequestBody final RegisterClientRequest request) {
        return Mono.just(request)
                .filter(RegisterClientRequest::hasContactForPreference)
                .switchIfEmpty(Mono.error(() -> new IllegalArgumentException(
                        "A %s preference requires the matching contact detail"
                                .formatted(request.notificationPreference()))))
                .map(valid -> new RegisterClientCommand(
                        valid.fullName(), valid.email(), valid.phone(), valid.notificationPreference()))
                .flatMap(registerClientUseCase::registerClient)
                .map(ClientResponse::from);
    }

    /** The token's subject must match the client being read, or this is an IDOR. */
    @Operation(summary = "Read a client's balance and active subscriptions")
    @PreAuthorize("#clientId == authentication.name or hasRole('ADMIN')")
    @GetMapping("/{clientId}")
    public Mono<ClientResponse> getClient(@PathVariable final String clientId) {
        return getClientBalanceUseCase.findClientBalance(ClientId.of(clientId))
                .map(ClientResponse::from);
    }
}
