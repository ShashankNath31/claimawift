package com.claimswift.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global Filter to add Correlation ID to all requests
 * Enables distributed tracing across microservices
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new Correlation ID: {}", correlationId);
        } else {
            log.debug("Using existing Correlation ID: {}", correlationId);
        }
        
        final String finalCorrelationId = correlationId;
        
        // Add to MDC for logging
        MDC.put(CORRELATION_ID_KEY, finalCorrelationId);
        exchange.getAttributes().put(CORRELATION_ID_KEY, finalCorrelationId);
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
        
        // Add to request headers
        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(r -> r.headers(headers -> headers.set(CORRELATION_ID_HEADER, finalCorrelationId)))
                .build();
        
        return chain.filter(modifiedExchange)
                .doFinally(signalType -> MDC.remove(CORRELATION_ID_KEY));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
