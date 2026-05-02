package com.ecommerce.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

@Slf4j
@Component
public class TokenBlacklistFilter extends OncePerRequestFilter {

    private static final URI BASE_TYPE = URI.create("https://api.ecommerce.com/errors/");

    private final TokenBlacklistService tokenBlacklistService;

    public TokenBlacklistFilter(TokenBlacklistService tokenBlacklistService) {
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (tokenBlacklistService.isTokenBlacklisted(token)) {
                log.warn("Blacklisted token used: {}", token.substring(0, Math.min(20, token.length())) + "...");

                ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
                problem.setType(URI.create(BASE_TYPE + "token-revoked"));
                problem.setTitle("Token Revoked");
                problem.setDetail("This token has been revoked. Please login again.");
                problem.setProperty("timestamp", Instant.now());

                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(problemToJson(problem));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String problemToJson(ProblemDetail problem) {
        return String.format(
            "{\"type\":\"%s\",\"title\":\"%s\",\"detail\":\"%s\",\"status\":%d,\"timestamp\":\"%s\"}",
            problem.getType(),
            problem.getTitle(),
            problem.getDetail(),
            problem.getStatus(),
            problem.getProperties().get("timestamp")
        );
    }
}
