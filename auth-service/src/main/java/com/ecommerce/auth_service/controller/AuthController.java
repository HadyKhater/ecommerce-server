package com.ecommerce.auth_service.controller;

import com.ecommerce.auth_service.dto.*;
import com.ecommerce.auth_service.security.TokenBlacklistService;
import com.ecommerce.auth_service.security.TokenProvider;
import com.ecommerce.auth_service.service.IUserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IUserService userService;
    private final TokenBlacklistService tokenBlacklistService;
    private final TokenProvider tokenProvider;

    public AuthController(IUserService userService, TokenBlacklistService tokenBlacklistService, TokenProvider tokenProvider) {
        this.userService = userService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.created(URI.create("/auth/users/" + response.id()))
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = userService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("X-User-Email") String email) {
        UserResponse response = userService.getCurrentUser(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistService.blacklistToken(token);
            log.info("User logged out, token blacklisted");
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        String publicKey = tokenProvider.getPublicKeyBase64();
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }
}
