package com.yam.funds.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI fundsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("YAM Funds API")
                        .version("v1")
                        .description("""
                                Lets clients subscribe to investment funds, cancel subscriptions and \
                                read their transaction history.

                                Every client starts with an opening balance of COP $500,000. Subscribing \
                                debits the linked amount; cancelling returns it in full.

                                Operations that move money require an `Idempotency-Key` header. Repeating \
                                a request with the same key returns the original result rather than \
                                moving money twice.""")
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("On the local profile, obtain one from POST /dev/token")));
    }
}
