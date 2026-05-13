package com.ecommerce.product_service.repository;

import com.ecommerce.product_service.entity.PromotionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface PromotionCategoryRepository extends JpaRepository<PromotionCategory, UUID> {
    List<PromotionCategory> findByPromotionId(UUID promotionId);

    @Modifying
    @Transactional
    void deleteByPromotionId(UUID promotionId);

    boolean existsByPromotionIdAndCategoryId(UUID promotionId, UUID categoryId);
}
