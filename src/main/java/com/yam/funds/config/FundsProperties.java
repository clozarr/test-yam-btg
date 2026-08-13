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
 * @param security    authentication settings
 * @param kafka       topic names
 * @param outbox      settings for the relay that forwards recorded events
 */
@ConfigurationProperties(prefix = "app")
public record FundsProperties(
        Client client,
        String currency,
        Idempotency idempotency,
        Security security,
        Kafka kafka,
        Outbox outbox) {

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

    /**
     * @param jwt bearer token settings
     */
    public record Security(Jwt jwt) {

        /**
         * @param secret HMAC signing key; must be overridden outside local development
         * @param issuer value placed in, and required from, the {@code iss} claim
         * @param ttl    how long a minted token stays valid
         */
        public record Jwt(String secret, String issuer, Duration ttl) {
        }
    }

    /**
     * @param bootstrapServers broker addresses
     * @param consumerGroupId  group the notification consumer joins
     * @param topics           destinations events are published to
     */
    public record Kafka(String bootstrapServers, String consumerGroupId, Topics topics) {

        /**
         * @param subscriptionNotifications carries the notice a client receives after
         *                                  subscribing to a fund
         */
        public record Topics(String subscriptionNotifications) {
        }
    }

    /**
     * @param relay how the outbox relay paces itself
     */
    public record Outbox(Relay relay) {

        /**
         * @param interval  delay between polling cycles
         * @param batchSize maximum events forwarded per cycle
         */
        public record Relay(Duration interval, int batchSize) {
        }
    }
}
