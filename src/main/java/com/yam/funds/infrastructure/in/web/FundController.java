package com.yam.funds.infrastructure.in.web;

import com.yam.funds.domain.port.in.ListFundsUseCase;
import com.yam.funds.infrastructure.in.web.dto.FundResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "Funds", description = "Catalogue of funds available for subscription")
@RestController
@RequestMapping(value = "/api/v1/funds", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class FundController {

    private final ListFundsUseCase listFundsUseCase;

    @Operation(summary = "List the funds currently open to new subscriptions")
    @GetMapping
    public Flux<FundResponse> listFunds() {
        return listFundsUseCase.findAvailableFunds().map(FundResponse::from);
    }
}
