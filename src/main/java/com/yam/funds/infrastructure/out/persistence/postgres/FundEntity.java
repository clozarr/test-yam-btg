package com.yam.funds.infrastructure.out.persistence.postgres;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

/**
 * Stored form of a fund.
 *
 * <p>The catalogue is master data and lives in PostgreSQL rather than MongoDB: it is
 * read-often, written almost never, and takes no part in any transaction that moves
 * money.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("fund")
public class FundEntity {

    @Id
    @Column("id")
    private String id;

    @Column("name")
    private String name;

    /** NUMERIC in the schema, so the exact minimum survives the round trip. */
    @Column("minimum_amount")
    private BigDecimal minimumAmount;

    @Column("currency")
    private String currency;

    @Column("category")
    private String category;

    @Column("active")
    private boolean active;
}
