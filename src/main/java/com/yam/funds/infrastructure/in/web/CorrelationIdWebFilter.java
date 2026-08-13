package com.yam.funds.infrastructure.in.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Gives every request a correlation id and puts it in the Reactor context.
 *
 * <p>The Reactor context, not a ThreadLocal: a reactive request hops threads freely, so
 * anything stored per-thread would be attached to whichever worker happened to run the
 * first operator and lost at the next boundary.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        final String correlationId = resolve(exchange);
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        exchange.getAttributes().put(CONTEXT_KEY, correlationId);

        return chain.filter(exchange)
                .contextWrite(Context.of(CONTEXT_KEY, correlationId));
    }

    /** Honours an inbound id so a trace survives across service hops. */
    private String resolve(final ServerWebExchange exchange) {
        final String inbound = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        return inbound == null || inbound.isBlank() ? UUID.randomUUID().toString() : inbound;
    }

    public static String currentCorrelationId(final ServerWebExchange exchange) {
        final Object value = exchange.getAttributes().get(CONTEXT_KEY);
        return value == null ? null : value.toString();
    }
}
