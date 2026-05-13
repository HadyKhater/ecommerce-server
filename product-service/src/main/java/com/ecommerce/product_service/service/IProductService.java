package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.*;

import java.util.List;
import java.util.UUID;

public interface IProductService {

    PagedResponse<ProductDTO> searchProducts(ProductSearchRequest request);

    PagedResponse<ProductDTO> getAllProductsForAdmin(ProductSearchRequest request);

    List<ProductDTO> getAllProducts();

    ProductDTO getProduct(UUID id);

    ProductDTO createProduct(CreateProductRequest request);

    ProductDTO updateProduct(UUID id, UpdateProductRequest request);

    void deleteProduct(UUID id);

    List<ProductImageResponse> getProductImages(UUID productId);

    ProductImageResponse addProductImage(UUID productId, ProductImageRequest request);

    ProductImageResponse updateProductImage(UUID imageId, ProductImageRequest request);

    void deleteProductImage(UUID imageId);

    List<ProductImageResponse> reorderProductImages(UUID productId, List<UUID> imageIds);

    ProductImageResponse setPrimaryImage(UUID productId, UUID imageId);
}
