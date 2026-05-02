package com.ecommerce.api_gateway.filter;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class EurekaDependencyFilter implements GlobalFilter, Ordered {

    private final DiscoveryClient discoveryClient;

    public EurekaDependencyFilter(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        // Check if Eureka is available by trying to get services
        try {
            var services = discoveryClient.getServices();
            if (services == null || services.isEmpty()) {
                // No services from Eureka - return 503
                exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return exchange.getResponse().setComplete();
            }
        } catch (Exception e) {
            // Cannot connect to Eureka - return 503
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return exchange.getResponse().setComplete();
        }

        // Eureka is available, continue with the request
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Highest priority - run before all other filters
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
