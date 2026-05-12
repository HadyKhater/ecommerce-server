package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.BannerRequest;
import com.ecommerce.product_service.dto.BannerResponse;
import com.ecommerce.product_service.entity.Banner;
import com.ecommerce.product_service.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BannerService implements IBannerService {

    private final BannerRepository bannerRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BannerResponse> getAllBanners() {
        return bannerRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerResponse> getActiveBanners() {
        return bannerRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BannerResponse getBannerById(UUID id) {
        return bannerRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Banner not found with id: " + id));
    }

    @Override
    @Transactional
    public BannerResponse createBanner(BannerRequest request) {
        Banner banner = new Banner();
        banner.setTitle(request.title());
        banner.setSubtitle(request.subtitle());
        banner.setImageUrl(request.imageUrl());
        banner.setLinkUrl(request.linkUrl());
        banner.setActive(request.active());
        banner.setDisplayOrder(request.displayOrder());

        Banner savedBanner = bannerRepository.save(banner);
        return mapToResponse(savedBanner);
    }

    @Override
    @Transactional
    public BannerResponse updateBanner(UUID id, BannerRequest request) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner not found with id: " + id));

        banner.setTitle(request.title());
        banner.setSubtitle(request.subtitle());
        banner.setImageUrl(request.imageUrl());
        banner.setLinkUrl(request.linkUrl());
        banner.setActive(request.active());
        banner.setDisplayOrder(request.displayOrder());

        Banner updatedBanner = bannerRepository.save(banner);
        return mapToResponse(updatedBanner);
    }

    @Override
    @Transactional
    public void deleteBanner(UUID id) {
        bannerRepository.deleteById(id);
    }

    @Override
    @Transactional
    public BannerResponse toggleActive(UUID id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner not found with id: " + id));
        banner.setActive(!banner.isActive());
        Banner updatedBanner = bannerRepository.save(banner);
        return mapToResponse(updatedBanner);
    }

    private BannerResponse mapToResponse(Banner banner) {
        return new BannerResponse(
                banner.getId(),
                banner.getTitle(),
                banner.getSubtitle(),
                banner.getImageUrl(),
                banner.getLinkUrl(),
                banner.isActive(),
                banner.getDisplayOrder(),
                banner.getCreatedAt()
        );
    }
}
