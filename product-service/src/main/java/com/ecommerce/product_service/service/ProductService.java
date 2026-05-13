package com.ecommerce.product_service.service;

import com.ecommerce.product_service.client.InventoryClient;
import com.ecommerce.product_service.dto.*;
import com.ecommerce.product_service.entity.Category;
import com.ecommerce.product_service.entity.Product;
import com.ecommerce.product_service.entity.ProductImage;
import com.ecommerce.product_service.exception.CategoryNotFoundException;
import com.ecommerce.product_service.exception.ProductNotFoundException;
import com.ecommerce.product_service.repository.CategoryRepository;
import com.ecommerce.product_service.repository.ProductImageRepository;
import com.ecommerce.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService implements IProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryClient inventoryClient;

    @Override
    @Cacheable(value = "product-search", key = "#request.hashCode()")
    public PagedResponse<ProductDTO> searchProducts(ProductSearchRequest request) {
        log.debug("Searching products with request: {}", request);

        Pageable pageable = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by("createdAt").descending()
        );

        Page<Product> productPage = productRepository.searchProducts(
                request.keyword(),
                request.categoryId(),
                request.minPrice(),
                request.maxPrice(),
                pageable
        );

        List<ProductDTO> products = productPage.getContent().stream()
                .map(this::mapToDTO)
                .toList();

        return new PagedResponse<>(
                products,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.isFirst(),
                productPage.isLast()
        );
    }

    @Override
    public PagedResponse<ProductDTO> getAllProductsForAdmin(ProductSearchRequest request) {
        log.debug("Fetching all products for admin with request: {}", request);

        Pageable pageable = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by("createdAt").descending()
        );

        Page<Product> productPage = productRepository.findAllForAdmin(
                request.keyword(),
                request.categoryId(),
                request.minPrice(),
                request.maxPrice(),
                request.active(),
                pageable
        );

        List<ProductDTO> products = productPage.getContent().stream()
                .map(this::mapToDTO)
                .toList();

        return new PagedResponse<>(
                products,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.isFirst(),
                productPage.isLast()
        );
    }

    @Override
    @Cacheable(value = "all-products")
    public List<ProductDTO> getAllProducts() {
        log.debug("Fetching all products");

        return productRepository.findAllByDeletedFalse().stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductDTO getProduct(UUID id) {
        log.debug("Getting product with id: {}", id);

        Product product = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        return mapToDTO(product);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
    public ProductDTO createProduct(CreateProductRequest request) {
        log.info("Creating new product: {}", request.name());

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(category);
        
        String sku = (request.sku() == null || request.sku().isBlank()) 
                     ? UUID.randomUUID().toString() 
                     : request.sku();
        product.setSku(sku);
        
        product.setActive(request.active() != null ? request.active() : true);
        product.setImageUrls(joinImageUrls(request.imageUrls()));
        product.setDeleted(false);

        Product savedProduct = productRepository.save(product);
        log.info("Product created with id: {}", savedProduct.getId());

        // Always initialize inventory in Inventory Service for new products
        try {
            inventoryClient.initializeInventory(savedProduct.getId(),
                new InventoryClient.InitializeRequest(0));
            log.info("Inventory initialized for product: {} with quantity: 0",
                savedProduct.getId());
        } catch (Exception e) {
            // Log error but don't fail product creation
            // Inventory can be initialized later via admin panel
            log.warn("Failed to initialize inventory for product: {}. Error: {}",
                savedProduct.getId(), e.getMessage());
        }

        return mapToDTO(savedProduct);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
    public ProductDTO updateProduct(UUID id, UpdateProductRequest request) {
        log.info("Updating product with id: {}", id);

        Product product = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.imageUrls() != null) {
            product.setImageUrls(joinImageUrls(request.imageUrls()));
        }
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));
            product.setCategory(category);
        }
        if (request.sku() != null) {
            product.setSku(request.sku());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated with id: {}", updatedProduct.getId());

        return mapToDTO(updatedProduct);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
    public void deleteProduct(UUID id) {
        log.info("Soft deleting product with id: {}", id);

        Product product = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        log.info("Found product to delete: {} (deleted={})", product.getName(), product.isDeleted());

        product.setDeleted(true);
        Product saved = productRepository.save(product);

        log.info("Product soft deleted successfully: {} (deleted={})", saved.getId(), saved.isDeleted());
    }

    @Override
    public List<ProductImageResponse> getProductImages(UUID productId) {
        log.debug("Fetching images for product: {}", productId);
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId).stream()
                .map(this::mapToImageResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
    public ProductImageResponse addProductImage(UUID productId, ProductImageRequest request) {
        log.info("Adding image to product: {}", productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(request.imageUrl())
                .displayOrder(request.displayOrder())
                .isPrimary(request.isPrimary())
                .build();

        if (image.isPrimary()) {
            // Unset other primary images
            productImageRepository.findByProductId(productId).forEach(img -> {
                if (img.isPrimary()) {
                    img.setPrimary(false);
                    productImageRepository.save(img);
                }
            });
        }

        ProductImage saved = productImageRepository.save(image);
        return mapToImageResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
    public ProductImageResponse updateProductImage(UUID imageId, ProductImageRequest request) {
        log.info("Updating product image: {}", imageId);
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found: " + imageId));

        image.setImageUrl(request.imageUrl());
        image.setDisplayOrder(request.displayOrder());
        
        if (request.isPrimary() && !image.isPrimary()) {
            // Set as primary, unset others
            productImageRepository.findByProductId(image.getProduct().getId()).forEach(img -> {
                if (img.isPrimary()) {
                    img.setPrimary(false);
                    productImageRepository.save(img);
                }
            });
            image.setPrimary(true);
        } else {
            image.setPrimary(request.isPrimary());
        }

        ProductImage saved = productImageRepository.save(image);
        return mapToImageResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
    public void deleteProductImage(UUID imageId) {
        log.info("Deleting product image: {}", imageId);
        productImageRepository.deleteById(imageId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
    public List<ProductImageResponse> reorderProductImages(UUID productId, List<UUID> imageIds) {
        log.info("Reordering images for product: {}", productId);
        List<ProductImage> images = productImageRepository.findByProductId(productId);
        
        for (int i = 0; i < imageIds.size(); i++) {
            UUID id = imageIds.get(i);
            int order = i;
            images.stream()
                    .filter(img -> img.getId().equals(id))
                    .findFirst()
                    .ifPresent(img -> {
                        img.setDisplayOrder(order);
                        productImageRepository.save(img);
                    });
        }

        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId).stream()
                .map(this::mapToImageResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
    public ProductImageResponse setPrimaryImage(UUID productId, UUID imageId) {
        log.info("Setting primary image: {} for product: {}", imageId, productId);
        List<ProductImage> images = productImageRepository.findByProductId(productId);
        
        ProductImage primaryImage = null;
        for (ProductImage img : images) {
            if (img.getId().equals(imageId)) {
                img.setPrimary(true);
                primaryImage = img;
            } else {
                img.setPrimary(false);
            }
            productImageRepository.save(img);
        }

        if (primaryImage == null) {
            throw new RuntimeException("Image not found: " + imageId);
        }

        return mapToImageResponse(primaryImage);
    }

    private ProductDTO mapToDTO(Product product) {
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                splitImageUrls(product.getImageUrls()),
                product.getImages().stream().map(this::mapToImageResponse).toList(),
                product.getSku(),
                product.getActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    /** Join a list of URLs into a comma-separated string for storage. */
    private String joinImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        return String.join(",", urls);
    }

    /** Split a comma-separated string into a list of URLs. */
    private List<String> splitImageUrls(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return List.of(raw.split(","));
    }

    private ProductImageResponse mapToImageResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getImageUrl(),
                image.getDisplayOrder(),
                image.isPrimary(),
                image.getCreatedAt()
        );
    }
}
