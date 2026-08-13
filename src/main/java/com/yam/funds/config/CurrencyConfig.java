package com.yam.funds.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Currency;

/**
 * The currency every amount in this service is denominated in.
 *
 * <p>Resolved once from configuration so the web layer never hard-codes it, and an
 * invalid ISO code fails at startup rather than on the first request that moves money.
 */
@Configuration
@RequiredArgsConstructor
public class CurrencyConfig {

    private final FundsProperties properties;

    @Bean
    public Currency currency() {
        return Currency.getInstance(properties.currency());
    }
}
