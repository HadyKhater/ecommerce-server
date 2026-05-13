package com.ecommerce.product_service.controller;

import com.ecommerce.product_service.dto.PromotionRequest;
import com.ecommerce.product_service.dto.PromotionResponse;
import com.ecommerce.product_service.dto.ApplyPromoCodeRequest;
import com.ecommerce.product_service.dto.ValidatePromoCodeRequest;
import com.ecommerce.product_service.dto.ValidatePromoCodeResponse;
import com.ecommerce.product_service.service.IPromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final IPromotionService promotionService;

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ValidatePromoCodeResponse> validatePromoCode(
            @Valid @RequestBody ValidatePromoCodeRequest request) {
        log.debug("POST /promotions/validate - Validating code: {}", request.code());
        return ResponseEntity.ok(promotionService.validatePromoCode(request));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Void> applyPromotion(@Valid @RequestBody ApplyPromoCodeRequest request) {
        log.info("POST /promotions/apply - Applying promo code: {}", request.code());
        promotionService.applyPromotion(request.code(), request.userId(), request.userEmail());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<PromotionResponse>> getActivePromotions() {
        log.debug("GET /promotions/active - Fetching active promotions");
        return ResponseEntity.ok(promotionService.getActivePromotions());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<PromotionResponse>> getAllPromotions() {
        log.debug("GET /promotions - Fetching all promotions");
        return ResponseEntity.ok(promotionService.getAllPromotions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromotionResponse> getPromotionById(@PathVariable UUID id) {
        log.debug("GET /promotions/{} - Fetching promotion by ID", id);
        return ResponseEntity.ok(promotionService.getPromotionById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromotionResponse> createPromotion(@Valid @RequestBody PromotionRequest request) {
        log.info("POST /promotions - Creating new promotion: {}", request.name());
        PromotionResponse createdPromotion = promotionService.createPromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPromotion);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromotionResponse> updatePromotion(
            @PathVariable UUID id,
            @Valid @RequestBody PromotionRequest request) {
        log.info("PUT /promotions/{} - Updating promotion", id);
        PromotionResponse updatedPromotion = promotionService.updatePromotion(id, request);
        return ResponseEntity.ok(updatedPromotion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePromotion(@PathVariable UUID id) {
        log.info("DELETE /promotions/{} - Deleting promotion", id);
        promotionService.deletePromotion(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromotionResponse> toggleActive(@PathVariable UUID id) {
        log.info("PATCH /promotions/{}/toggle - Toggling active status", id);
        PromotionResponse toggledPromotion = promotionService.toggleActive(id);
        return ResponseEntity.ok(toggledPromotion);
    }
}
