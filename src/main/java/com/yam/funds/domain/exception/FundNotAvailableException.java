package com.yam.funds.domain.exception;

/** Raised when a fund exists but is no longer open to new subscriptions. */
public class FundNotAvailableException extends DomainException {

    public static final String ERROR_CODE = "FUND_NOT_AVAILABLE";

    public FundNotAvailableException(final String fundName) {
        super(ERROR_CODE, "Fund %s is not open to new subscriptions".formatted(fundName));
    }
}
