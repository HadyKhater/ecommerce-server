package com.ecommerce.auth_service.service;

import com.ecommerce.auth_service.dto.*;
import com.ecommerce.auth_service.model.User;
import com.ecommerce.auth_service.repository.UserRepository;
import com.ecommerce.auth_service.security.TokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService implements IUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    public UserService(UserRepository userRepository, 
                       PasswordEncoder passwordEncoder,
                       TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());

        User savedUser = userRepository.save(user);
        log.info("User registered: {}", savedUser.getEmail());

        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        log.info("User logged in: {}", user.getEmail());

        return new TokenResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpiration()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse refreshToken(RefreshRequest request) {
        if (!tokenProvider.validateToken(request.refreshToken())) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String email = tokenProvider.getEmailFromToken(request.refreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        log.info("Token refreshed for user: {}", user.getEmail());

        return new TokenResponse(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpiration()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return mapToUserResponse(user);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles(),
                user.getCreatedAt()
        );
    }
}
