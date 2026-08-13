package com.yam.funds.infrastructure.out.persistence.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stored form of a monetary amount.
 *
 * <p>The amount is held as a string rather than a native numeric type. Spring Data's
 * default representation for {@code BigDecimal} has changed between versions, and a
 * silent switch to a binary floating point type would corrupt balances. A string is
 * exact, portable and independent of driver defaults; the scale is fixed by the domain
 * on the way back in.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoneyDocument {

    private String amount;

    private String currency;
}
