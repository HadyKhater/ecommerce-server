package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.PromotionRequest;
import com.ecommerce.product_service.dto.PromotionResponse;
import com.ecommerce.product_service.dto.ValidatePromoCodeRequest;
import com.ecommerce.product_service.dto.ValidatePromoCodeResponse;

import java.util.List;
import java.util.UUID;

public interface IPromotionService {
    List<PromotionResponse> getAllPromotions();
    List<PromotionResponse> getActivePromotions();
    PromotionResponse getPromotionById(UUID id);
    PromotionResponse createPromotion(PromotionRequest request);
    PromotionResponse updatePromotion(UUID id, PromotionRequest request);
    void deletePromotion(UUID id);
    PromotionResponse toggleActive(UUID id);
    ValidatePromoCodeResponse validatePromoCode(ValidatePromoCodeRequest request);
    void applyPromotion(UUID promotionId);
}
