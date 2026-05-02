package com.ecommerce.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitingFilter implements GlobalFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";

    private final ConcurrentHashMap<String, RateLimitEntry> requestCounts = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String clientIp = getClientIp(request);
        RateLimitEntry entry = requestCounts.computeIfAbsent(clientIp, k -> new RateLimitEntry());

        synchronized (entry) {
            Instant now = Instant.now();

            // Reset if window has passed
            if (now.isAfter(entry.windowEnd)) {
                entry.count.set(0);
                entry.windowEnd = now.plus(WINDOW_SIZE);
            }

            int currentCount = entry.count.incrementAndGet();
            int remaining = Math.max(0, MAX_REQUESTS_PER_MINUTE - currentCount);
            long resetSeconds = Duration.between(now, entry.windowEnd).getSeconds();

            // Add rate limit headers to response
            response.getHeaders().add(RATE_LIMIT_REMAINING_HEADER, String.valueOf(remaining));
            response.getHeaders().add(RATE_LIMIT_RESET_HEADER, String.valueOf(resetSeconds));

            if (currentCount > MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for IP: {} (count: {})", clientIp, currentCount);
                return tooManyRequests(response, resetSeconds);
            }
        }

        return chain.filter(exchange);
    }

    private String getClientIp(ServerHttpRequest request) {
        // Check X-Forwarded-For header (for proxies)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // Check X-Real-IP header
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Fall back to remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            InetAddress address = remoteAddress.getAddress();
            if (address != null) {
                return address.getHostAddress();
            }
        }

        return "unknown";
    }

    private Mono<Void> tooManyRequests(ServerHttpResponse response, long retryAfter) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().add("Retry-After", String.valueOf(retryAfter));

        // RFC 7807 Problem Detail
        String problemDetail = String.format(
            "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded. Try again in %d seconds.\"}",
            retryAfter
        );

        DataBuffer buffer = response.bufferFactory().wrap(problemDetail.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static class RateLimitEntry {
        final AtomicInteger count = new AtomicInteger(0);
        volatile Instant windowEnd = Instant.now().plus(WINDOW_SIZE);
    }
}
