package com.yam.funds.domain.exception;

import com.yam.funds.domain.model.FundId;

/** Raised when the requested fund does not exist in the catalogue. */
public class FundNotFoundException extends DomainException {

    public static final String ERROR_CODE = "FUND_NOT_FOUND";

    public FundNotFoundException(final FundId fundId) {
        super(ERROR_CODE, "Fund %s does not exist".formatted(fundId));
    }
}
