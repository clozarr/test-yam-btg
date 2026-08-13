package com.yam.funds.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the clock use cases read the current instant from.
 *
 * <p>Injected rather than called statically so tests can freeze time and assert on
 * exact timestamps in the ledger.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
