package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.PromotionRequest;
import com.ecommerce.product_service.dto.PromotionResponse;
import com.ecommerce.product_service.dto.ValidatePromoCodeRequest;
import com.ecommerce.product_service.dto.ValidatePromoCodeResponse;
import com.ecommerce.product_service.entity.DiscountType;
import com.ecommerce.product_service.entity.Promotion;
import com.ecommerce.product_service.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionService implements IPromotionService {

    private final PromotionRepository promotionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getAllPromotions() {
        return promotionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getActivePromotions() {
        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.findByActiveTrueAndStartDateBeforeAndEndDateAfter(now, now).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(UUID id) {
        return promotionRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Promotion not found with id: " + id));
    }

    @Override
    @Transactional
    public PromotionResponse createPromotion(PromotionRequest request) {
        Promotion promotion = new Promotion();
        updatePromotionFields(promotion, request);
        return mapToResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(UUID id, PromotionRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promotion not found with id: " + id));
        updatePromotionFields(promotion, request);
        return mapToResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public void deletePromotion(UUID id) {
        promotionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public PromotionResponse toggleActive(UUID id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promotion not found with id: " + id));
        promotion.setActive(!promotion.isActive());
        return mapToResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional(readOnly = true)
    public ValidatePromoCodeResponse validatePromoCode(ValidatePromoCodeRequest request) {
        Optional<Promotion> promotionOpt = promotionRepository.findByCode(request.code());
        if (promotionOpt.isEmpty()) {
            return new ValidatePromoCodeResponse(false, "Invalid promo code.", BigDecimal.ZERO, null);
        }

        Promotion promotion = promotionOpt.get();

        if (!promotion.isActive()) {
            return new ValidatePromoCodeResponse(false, "Promo code is inactive.", BigDecimal.ZERO, null);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(promotion.getStartDate()) || now.isAfter(promotion.getEndDate())) {
            return new ValidatePromoCodeResponse(false, "Promo code is expired or not yet active.", BigDecimal.ZERO, null);
        }

        if (promotion.getUsageLimit() != null && promotion.getUsageCount() >= promotion.getUsageLimit()) {
            return new ValidatePromoCodeResponse(false, "Promo code usage limit exceeded.", BigDecimal.ZERO, null);
        }

        if (promotion.getMinimumOrderAmount() != null && request.orderAmount().compareTo(promotion.getMinimumOrderAmount()) < 0) {
            return new ValidatePromoCodeResponse(false, "Minimum order amount not met.", BigDecimal.ZERO, null);
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (promotion.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            discountAmount = promotion.getDiscountValue();
        } else if (promotion.getDiscountType() == DiscountType.PERCENTAGE) {
            discountAmount = request.orderAmount().multiply(promotion.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        // Ensure discount doesn't exceed order amount
        if (discountAmount.compareTo(request.orderAmount()) > 0) {
            discountAmount = request.orderAmount();
        }

        return new ValidatePromoCodeResponse(true, "Promo code applied successfully.", discountAmount, mapToResponse(promotion));
    }

    @Override
    @Transactional
    public void applyPromotion(UUID promotionId) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new RuntimeException("Promotion not found with id: " + promotionId));
        promotion.setUsageCount(promotion.getUsageCount() + 1);
        promotionRepository.save(promotion);
    }

    private void updatePromotionFields(Promotion promotion, PromotionRequest request) {
        promotion.setName(request.name());
        promotion.setCode(request.code());
        promotion.setDescription(request.description());
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue());
        promotion.setMinimumOrderAmount(request.minimumOrderAmount());
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());
        promotion.setActive(request.active());
        promotion.setUsageLimit(request.usageLimit());
        promotion.setApplicableToAll(request.applicableToAll());
    }

    private PromotionResponse mapToResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getName(),
                promotion.getCode(),
                promotion.getDescription(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getMinimumOrderAmount(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.isActive(),
                promotion.getUsageLimit(),
                promotion.getUsageCount(),
                promotion.isApplicableToAll(),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt()
        );
    }
}
