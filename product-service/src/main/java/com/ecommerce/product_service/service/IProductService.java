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
}
