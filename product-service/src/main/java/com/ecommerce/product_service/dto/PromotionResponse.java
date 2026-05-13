package com.ecommerce.product_service.dto;

import com.ecommerce.product_service.entity.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        Integer perUserLimit,
        long userUsageCount,
        boolean applicableToAll,
        List<UUID> categoryIds,
        List<String> categoryNames,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
