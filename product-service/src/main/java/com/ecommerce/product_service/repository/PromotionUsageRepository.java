package com.ecommerce.product_service.repository;

import com.ecommerce.product_service.entity.PromotionUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, UUID> {
    long countByPromotionIdAndUserId(UUID promotionId, UUID userId);
    List<PromotionUsage> findByPromotionIdOrderByUsedAtDesc(UUID promotionId);
    boolean existsByPromotionIdAndUserId(UUID promotionId, UUID userId);
}
