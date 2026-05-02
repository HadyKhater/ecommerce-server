package com.ecommerce.auth_service.dto;

import com.ecommerce.auth_service.model.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        Set<Role> roles,
        Instant createdAt
) {
}
