package com.ecommerce.product_service.dto;

import com.ecommerce.product_service.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PromotionRequest(
        @NotBlank(message = "Name is required")
        String name,
        String code,
        String description,
        @NotNull(message = "Discount type is required")
        DiscountType discountType,
        @NotNull(message = "Discount value is required")
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        @NotNull(message = "Start date is required")
        LocalDateTime startDate,
        @NotNull(message = "End date is required")
        LocalDateTime endDate,
        boolean active,
        Integer usageLimit,
        Integer perUserLimit,
        boolean applicableToAll,
        List<UUID> categoryIds
) {}
