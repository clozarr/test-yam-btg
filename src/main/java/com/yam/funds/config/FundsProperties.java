package com.yam.funds.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Type-safe view of the {@code app.*} configuration.
 *
 * @param client      client-related business settings
 * @param currency    ISO code every monetary amount is denominated in
 * @param idempotency lifetimes governing idempotency records
 */
@ConfigurationProperties(prefix = "app")
public record FundsProperties(Client client, String currency, Idempotency idempotency) {

    /**
     * @param initialBalance opening balance every client is registered with
     */
    public record Client(BigDecimal initialBalance) {
    }

    /**
     * @param retention how long a completed record stays replayable
     * @param lease     how long a reservation is honoured before being treated as
     *                  abandoned by a crashed instance
     */
    public record Idempotency(Duration retention, Duration lease) {
    }
}
