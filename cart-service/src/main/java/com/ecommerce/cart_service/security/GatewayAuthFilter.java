package com.ecommerce.cart_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gateway authentication filter.
 * Trusts X-User-Email and X-Gateway-Secret headers forwarded by API Gateway.
 * No JWT parsing - Gateway handles all JWT validation.
 */
@Slf4j
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String gatewaySecretHeader = request.getHeader(GATEWAY_SECRET_HEADER);

        // Validate gateway secret to ensure request came through API Gateway
        if (gatewaySecretHeader == null || !gatewaySecretHeader.equals(gatewaySecret)) {
            log.warn("Invalid or missing gateway secret for request: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Invalid or missing gateway secret"}
                """);
            return;
        }

        String userEmail = request.getHeader(USER_EMAIL_HEADER);
        String userRolesHeader = request.getHeader(USER_ROLES_HEADER);

        if (userEmail != null && !userEmail.isEmpty()) {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // Parse roles from header
            if (userRolesHeader != null && !userRolesHeader.isEmpty()) {
                for (String role : userRolesHeader.split(",")) {
                    String trimmedRole = role.trim();
                    if (!trimmedRole.isEmpty()) {
                        String roleName = trimmedRole.startsWith("ROLE_") ? trimmedRole : "ROLE_" + trimmedRole;
                        authorities.add(new SimpleGrantedAuthority(roleName));
                    }
                }
            } else {
                // Default role if none provided
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }

            // Use email as the principal (not UUID)
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null,
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user: {}, roles: {}", userEmail, authorities);
        } else {
            log.warn("Missing X-User-Email header for request: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Missing user email header"}
                """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip filter for actuator endpoints and health check
        return path.startsWith("/actuator") || path.equals("/health");
    }
}
