-- Fund catalogue (master data).
--
-- NUMERIC(19, 2) rather than a floating point type: a fund's minimum subscription
-- amount is money, and binary floating point cannot represent it exactly.

CREATE TABLE IF NOT EXISTS fund (
    id             VARCHAR(36)    NOT NULL,
    name           VARCHAR(120)   NOT NULL,
    minimum_amount NUMERIC(19, 2) NOT NULL,
    currency       CHAR(3)        NOT NULL DEFAULT 'COP',
    category       VARCHAR(16)    NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_fund PRIMARY KEY (id),
    CONSTRAINT uq_fund_name UNIQUE (name),
    CONSTRAINT ck_fund_minimum_amount CHECK (minimum_amount > 0),
    CONSTRAINT ck_fund_category CHECK (category IN ('FPV', 'FIC'))
);

CREATE INDEX IF NOT EXISTS ix_fund_active ON fund (active);
