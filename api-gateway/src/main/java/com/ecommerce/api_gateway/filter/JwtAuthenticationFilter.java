package com.ecommerce.api_gateway.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter implements GlobalFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh");

    @Value("${jwt.public-key:#{null}}")
    private String publicKeyBase64;

    @Value("${jwt.public-routes:}")
    private List<String> publicRoutes;

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            if (publicKeyBase64 != null && !publicKeyBase64.isEmpty()) {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
                X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
                this.publicKey = keyFactory.generatePublic(publicKeySpec);
                log.info("JWT public key loaded successfully");
            } else {
                log.warn("JWT public key not configured. Waiting for dynamic key loading from auth-service.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize JWT public key", e);
            throw new RuntimeException("Failed to initialize JWT public key", e);
        }
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
        log.info("JWT public key dynamically set from auth-service");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        log.info("Gateway received request path: {}", path);

        // Skip JWT validation for public routes but still add gateway secret
        if (isPublicPath(path)) {
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(GATEWAY_SECRET_HEADER, gatewaySecret)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // Extract Authorization header
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange.getResponse(), "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // Validate token
        try {
            Claims claims = validateAndExtractClaims(token);
            if (claims == null) {
                return unauthorized(exchange.getResponse(), "Invalid token");
            }

            // Extract user info from claims
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            if (userId == null) {
                return unauthorized(exchange.getResponse(), "Token missing user ID");
            }

            // Add headers to forwarded request
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(USER_ID_HEADER, userId)
                    .header(USER_EMAIL_HEADER, email != null ? email : "")
                    .header(USER_ROLES_HEADER, roles != null ? String.join(",", roles) : "USER")
                    .header(GATEWAY_SECRET_HEADER, gatewaySecret)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            log.warn("Token expired: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), "Token has expired");
        } catch (SignatureException e) {
            log.warn("Invalid signature: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), "Invalid token signature");
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), "Token validation failed");
        }
    }

    private boolean isPublicPath(String path) {
        log.info("Checking if public path: '{}', publicRoutes: {}", path, publicRoutes);

        // Check hardcoded public paths (exact match)
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::equals);

        // Check configurable public routes (prefix match)
        if (!isPublic && publicRoutes != null && !publicRoutes.isEmpty()) {
            isPublic = publicRoutes.stream().anyMatch(path::startsWith);
        }

        log.info("isPublicPath result: {}", isPublic);
        return isPublic;
    }

    private Claims validateAndExtractClaims(String token) {
        if (publicKey == null) {
            log.error("Public key not configured");
            return null;
        }

        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String detail) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        // RFC 7807 Problem Detail
        String problemDetail = String.format(
                "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"%s\"}",
                detail.replace("\"", "\\\""));

        DataBuffer buffer = response.bufferFactory().wrap(problemDetail.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
