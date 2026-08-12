package com.yam.funds.domain.exception;

import com.yam.funds.domain.model.Money;

/** Raised when the requested amount is below the fund's minimum subscription amount. */
public class MinimumAmountNotMetException extends DomainException {

    public static final String ERROR_CODE = "MINIMUM_AMOUNT_NOT_MET";

    public MinimumAmountNotMetException(final String fundName, final Money minimumAmount, final Money requested) {
        super(ERROR_CODE, "Fund %s requires a minimum subscription amount of %s, but %s was requested"
                .formatted(fundName, minimumAmount, requested));
    }
}
