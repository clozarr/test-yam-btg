package com.yam.funds.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduled tasks the outbox relay depends on.
 *
 * <p>Kept separate so an integration test can exclude it and drive the relay by hand
 * instead of racing a background timer.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
