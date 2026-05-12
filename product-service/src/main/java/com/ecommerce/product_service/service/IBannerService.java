package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.BannerRequest;
import com.ecommerce.product_service.dto.BannerResponse;

import java.util.List;
import java.util.UUID;

public interface IBannerService {
    List<BannerResponse> getAllBanners();
    List<BannerResponse> getActiveBanners();
    BannerResponse getBannerById(UUID id);
    BannerResponse createBanner(BannerRequest request);
    BannerResponse updateBanner(UUID id, BannerRequest request);
    void deleteBanner(UUID id);
    BannerResponse toggleActive(UUID id);
}
