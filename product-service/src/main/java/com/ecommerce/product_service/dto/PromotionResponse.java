package com.ecommerce.product_service.dto;

import com.ecommerce.product_service.entity.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PromotionResponse(
        UUID id,
        String name,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderAmount,
        LocalDateTime startDate,
        LocalDateTime endDate,
        boolean active,
        Integer usageLimit,
        int usageCount,
        boolean applicableToAll,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
