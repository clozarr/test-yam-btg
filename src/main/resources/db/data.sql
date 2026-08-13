-- Fund catalogue as defined by the business specification.
--
-- Idempotent: the initialiser runs on every startup, so re-inserting must be a no-op
-- rather than a primary key violation.

INSERT INTO fund (id, name, minimum_amount, currency, category, active) VALUES
    ('1', 'FPV_AM_PACTUAL_RECAUDADORA',  75000.00, 'COP', 'FPV', TRUE),
    ('2', 'FPV_AM_PACTUAL_ECOPETROL',   125000.00, 'COP', 'FPV', TRUE),
    ('3', 'DEUDAPRIVADA',                50000.00, 'COP', 'FIC', TRUE),
    ('4', 'FDO-ACCIONES',               250000.00, 'COP', 'FIC', TRUE),
    ('5', 'FPV_AM_PACTUAL_DINAMICA',    100000.00, 'COP', 'FPV', TRUE)
ON CONFLICT (id) DO NOTHING;
