package com.ecommerce.auth_service.service;

import com.ecommerce.auth_service.dto.*;

public interface IUserService {

    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refreshToken(RefreshRequest request);

    UserResponse getCurrentUser(String email);
}
