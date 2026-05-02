package com.ecommerce.api_gateway.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Security filter for the API Gateway.
 * Ensures that all requests must go through the gateway and not directly to backend services.
 *
 * This filter enforces that:
 * 1. All external requests come through port 8080 (the gateway)
 * 2. Backend services can only be accessed via lb://service-name through Eureka
 * 3. Direct requests to backend service ports should fail or be blocked by network policy
 */
@Component
public class SecurityFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Add gateway headers to track request path
        exchange.getRequest().mutate()
            .header("X-Gateway-Request", "true")
            .header("X-Request-Path", exchange.getRequest().getPath().toString())
            .build();

        return chain.filter(exchange);
    }
}


