package com.ecommerce.api_gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Component
@Order(-2) // Higher priority than Spring's default exception handler
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        HttpStatus status = determineHttpStatus(ex);
        String title = determineTitle(status);
        String detail = determineDetail(ex, status);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        // RFC 7807 Problem Detail JSON
        String problemDetail = buildProblemDetailJson(status, title, detail, exchange.getRequest().getPath().value());

        log.error("Exception occurred: {} - {} (status: {})", ex.getClass().getSimpleName(), ex.getMessage(), status.value(), ex);

        DataBuffer buffer = response.bufferFactory().wrap(problemDetail.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            return (HttpStatus) ((ResponseStatusException) ex).getStatusCode();
        }
        if (ex instanceof io.jsonwebtoken.ExpiredJwtException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof io.jsonwebtoken.security.SignatureException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof io.jsonwebtoken.MalformedJwtException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof io.jsonwebtoken.UnsupportedJwtException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof SecurityException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        }

        // Default to 500 for unknown errors
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String determineTitle(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Error";
        };
    }

    private String determineDetail(Throwable ex, HttpStatus status) {
        // For 5xx errors, don't expose internal details
        if (status.is5xxServerError()) {
            return "An internal server error occurred. Please try again later.";
        }

        // For 4xx errors, provide helpful details
        String message = ex.getMessage();
        if (message == null || message.isEmpty()) {
            return status.getReasonPhrase();
        }

        // Escape JSON special characters
        return message.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String buildProblemDetailJson(HttpStatus status, String title, String detail, String instance) {
        return String.format(
            "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"instance\":\"%s\",\"timestamp\":\"%s\"}",
            title,
            status.value(),
            detail,
            instance,
            Instant.now().toString()
        );
    }
}
