package com.ecommerce.product_service.controller;

import com.ecommerce.product_service.dto.*;
import com.ecommerce.product_service.service.IProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final IProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<PagedResponse<ProductDTO>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        log.debug("GET /products - keyword: {}, categoryId: {}, page: {}, size: {}",
                keyword, categoryId, page, size);

        ProductSearchRequest request = new ProductSearchRequest(
                keyword, categoryId, minPrice, maxPrice, page, size, null
        );

        PagedResponse<ProductDTO> response = productService.searchProducts(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<ProductDTO>> getAllProductsForAdmin(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        log.debug("GET /products/admin/all - keyword: {}, categoryId: {}, active: {}, page: {}, size: {}",
                keyword, categoryId, active, page, size);

        ProductSearchRequest request = new ProductSearchRequest(
                keyword, categoryId, minPrice, maxPrice, page, size, active
        );

        PagedResponse<ProductDTO> response = productService.getAllProductsForAdmin(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        log.debug("GET /products/all - Fetching all products");

        List<ProductDTO> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<ProductDTO> getProduct(@PathVariable UUID id) {
        log.debug("GET /products/{}", id);

        ProductDTO product = productService.getProduct(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No authenticated user found"));
        }

        UUID userId = (UUID) authentication.getPrincipal();
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("roles", authentication.getAuthorities());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody CreateProductRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = authentication != null ? (UUID) authentication.getPrincipal() : null;

        log.info("POST /products - User: {} creating product: {}", userId, request.name());

        ProductDTO createdProduct = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        log.info("PUT /products/{} - Updating product", id);

        ProductDTO updatedProduct = productService.updateProduct(id, request);
        return ResponseEntity.ok(updatedProduct);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        log.info("DELETE /products/{} - Soft deleting product", id);

        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
