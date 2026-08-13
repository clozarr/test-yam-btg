package com.yam.funds.infrastructure.in.web.dto;

import com.yam.funds.domain.model.Fund;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A fund offered by the platform")
public record FundResponse(
        @Schema(example = "1") String id,
        @Schema(example = "FPV_AM_PACTUAL_RECAUDADORA") String name,
        MoneyResponse minimumAmount,
        @Schema(example = "FPV") String category) {

    public static FundResponse from(final Fund fund) {
        return new FundResponse(
                fund.id().value(),
                fund.name(),
                MoneyResponse.from(fund.minimumAmount()),
                fund.category().name());
    }
}
