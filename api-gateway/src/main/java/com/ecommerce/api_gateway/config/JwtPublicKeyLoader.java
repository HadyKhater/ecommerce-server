package com.ecommerce.api_gateway.config;

import com.ecommerce.api_gateway.filter.JwtAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class JwtPublicKeyLoader implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final int MAX_RETRIES = 10;
    private static final int RETRY_DELAY_SECONDS = 3;

    public JwtPublicKeyLoader(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        loadPublicKeyWithRetry();
    }

    private void loadPublicKeyWithRetry() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Attempting to fetch public key from auth-service (attempt {}/{})...", attempt, MAX_RETRIES);

                @SuppressWarnings("unchecked")
                Map<String, String> response = WebClient.builder()
                        .baseUrl(authServiceUrl)
                        .build()
                        .get()
                        .uri("/auth/public-key")
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, clientResponse ->
                                Mono.error(new RuntimeException("Auth service returned error: " + clientResponse.statusCode())))
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(5));

                if (response != null && response.containsKey("publicKey")) {
                    String publicKeyBase64 = response.get("publicKey");
                    PublicKey publicKey = decodePublicKey(publicKeyBase64);
                    jwtAuthenticationFilter.setPublicKey(publicKey);
                    log.info("Successfully loaded and configured JWT public key from auth-service");
                    log.info("Public key loaded successfully, length: {}", publicKeyBase64.length());
                    return;
                } else {
                    log.warn("Invalid response from auth-service: missing 'publicKey' field");
                }

            } catch (WebClientResponseException e) {
                log.warn("Auth service returned error (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
            } catch (Exception e) {
                log.warn("Failed to fetch public key (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting to retry");
                    break;
                }
            }
        }

        log.error("Failed to load public key after {} attempts. Protected routes will be inaccessible.", MAX_RETRIES);
    }

    private PublicKey decodePublicKey(String publicKeyBase64) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        return keyFactory.generatePublic(publicKeySpec);
    }
}
