package com.ecommerce.inventory_service.security;

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
import java.util.UUID;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String gatewaySecretHeader = request.getHeader("X-Gateway-Secret");

        if (gatewaySecretHeader == null || !gatewaySecretHeader.equals(gatewaySecret)) {
            log.warn("Invalid or missing gateway secret for request: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                {"type":"https://api.ecommerce.com/errors/forbidden","title":"Access Denied","status":403,"detail":"Invalid or missing gateway secret"}
                """);
            return;
        }

        String userIdHeader = request.getHeader("X-User-Id");
        String userRolesHeader = request.getHeader("X-User-Roles");

        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            List<GrantedAuthority> authorities = new ArrayList<>();

            if (userRolesHeader != null && !userRolesHeader.isEmpty()) {
                for (String role : userRolesHeader.split(",")) {
                    String trimmedRole = role.trim();
                    if (!trimmedRole.isEmpty()) {
                        String roleName = trimmedRole.startsWith("ROLE_") ? trimmedRole : "ROLE_" + trimmedRole;
                        authorities.add(new SimpleGrantedAuthority(roleName));
                    }
                }
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            UUID.fromString(userIdHeader),
                            null,
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user: {}, roles: {}", userIdHeader, userRolesHeader);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }
}
