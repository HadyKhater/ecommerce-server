package com.ecommerce.auth_service.security;

import com.ecommerce.auth_service.model.User;
import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class TokenProvider {

    private static final String USER_ID_CLAIM = "userId";
    private static final String EMAIL_CLAIM = "email";
    private static final String ROLES_CLAIM = "roles";

    @Value("${jwt.access-token-expiration:900}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800}")
    private long refreshTokenExpiration;

    @Value("${jwt.private-key:#{null}}")
    private String privateKeyBase64;

    @Value("${jwt.public-key:#{null}}")
    private String publicKeyBase64;

    @Value("${jwt.key-file-path:keys/auth-keypair.pem}")
    private String keyFilePath;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            if (privateKeyBase64 != null && publicKeyBase64 != null) {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64);
                PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
                this.privateKey = keyFactory.generatePrivate(privateKeySpec);

                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
                X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
                this.publicKey = keyFactory.generatePublic(publicKeySpec);
            } else {
                Path path = Paths.get(keyFilePath);
                if (Files.exists(path)) {
                    loadKeyPairFromFile(path);
                    log.info("Loaded existing RSA key pair from {}", keyFilePath);
                } else {
                    generateAndSaveKeyPair(path);
                    log.info("Generated new RSA-2048 key pair and saved to {}", keyFilePath);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RSA keys", e);
        }
    }

    private void loadKeyPairFromFile(Path path) throws Exception {
        String content = Files.readString(path);
        String[] parts = content.split("\\|\\|\\|");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid key file format");
        }

        byte[] privateKeyBytes = Base64.getDecoder().decode(parts[0]);
        byte[] publicKeyBytes = Base64.getDecoder().decode(parts[1]);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        this.privateKey = keyFactory.generatePrivate(privateKeySpec);

        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        this.publicKey = keyFactory.generatePublic(publicKeySpec);
    }

    private void generateAndSaveKeyPair(Path path) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();

        String privateKeyBase64Str = Base64.getEncoder().encodeToString(this.privateKey.getEncoded());
        String publicKeyBase64Str = Base64.getEncoder().encodeToString(this.publicKey.getEncoded());

        Files.createDirectories(path.getParent());
        Files.writeString(path, privateKeyBase64Str + "|||" + publicKeyBase64Str);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenExpiration, ChronoUnit.SECONDS);

        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim(USER_ID_CLAIM, user.getId().toString())
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(ROLES_CLAIM, user.getRoles())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshTokenExpiration, ChronoUnit.SECONDS);

        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim(USER_ID_CLAIM, user.getId().toString())
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(ROLES_CLAIM, user.getRoles())
                .claim("type", "refresh")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token has expired: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
            return false;
        } catch (SecurityException e) {
            log.warn("Invalid signature: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null: {}", e.getMessage());
            return false;
        }
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        String userId = claims.get(USER_ID_CLAIM, String.class);
        return UUID.fromString(userId);
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get(EMAIL_CLAIM, String.class);
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
