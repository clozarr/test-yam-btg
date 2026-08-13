package com.yam.funds.config;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Builds {@link FundsProperties} for tests.
 *
 * <p>Centralised so that adding a configuration section touches one place instead of
 * every test that happens to need the record.
 */
public final class FundsPropertiesFixture {

    private FundsPropertiesFixture() {
    }

    public static FundsProperties defaults() {
        return withIdempotency(Duration.ofHours(24), Duration.ofSeconds(30));
    }

    public static FundsProperties withIdempotency(final Duration retention, final Duration lease) {
        return new FundsProperties(
                new FundsProperties.Client(new BigDecimal("500000")),
                "COP",
                new FundsProperties.Idempotency(retention, lease),
                new FundsProperties.Security(new FundsProperties.Security.Jwt(
                        "test-secret-key-at-least-32-characters!!", "yam-funds", Duration.ofHours(1))),
                new FundsProperties.Kafka(
                        "localhost:9092",
                        "funds-test",
                        new FundsProperties.Kafka.Topics("funds.subscription.notifications")),
                new FundsProperties.Outbox(
                        new FundsProperties.Outbox.Relay(Duration.ofSeconds(2), 100)));
    }
}
