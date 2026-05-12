package com.ecommerce.product_service.controller;

import com.ecommerce.product_service.dto.BannerRequest;
import com.ecommerce.product_service.dto.BannerResponse;
import com.ecommerce.product_service.service.IBannerService;
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
@RequestMapping("/banners")
@RequiredArgsConstructor
public class BannerController {

    private final IBannerService bannerService;

    @GetMapping("/public")
    public ResponseEntity<List<BannerResponse>> getActiveBanners() {
        log.debug("GET /banners/public - Fetching active banners");
        return ResponseEntity.ok(bannerService.getActiveBanners());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BannerResponse>> getAllBanners() {
        log.debug("GET /banners - Fetching all banners");
        return ResponseEntity.ok(bannerService.getAllBanners());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BannerResponse> getBannerById(@PathVariable UUID id) {
        log.debug("GET /banners/{} - Fetching banner by ID", id);
        return ResponseEntity.ok(bannerService.getBannerById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BannerResponse> createBanner(@Valid @RequestBody BannerRequest request) {
        log.info("POST /banners - Creating new banner: {}", request.title());
        BannerResponse createdBanner = bannerService.createBanner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBanner);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BannerResponse> updateBanner(
            @PathVariable UUID id,
            @Valid @RequestBody BannerRequest request) {
        log.info("PUT /banners/{} - Updating banner", id);
        BannerResponse updatedBanner = bannerService.updateBanner(id, request);
        return ResponseEntity.ok(updatedBanner);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBanner(@PathVariable UUID id) {
        log.info("DELETE /banners/{} - Deleting banner", id);
        bannerService.deleteBanner(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BannerResponse> toggleActive(@PathVariable UUID id) {
        log.info("PATCH /banners/{}/toggle - Toggling active status", id);
        BannerResponse toggledBanner = bannerService.toggleActive(id);
        return ResponseEntity.ok(toggledBanner);
    }
}
