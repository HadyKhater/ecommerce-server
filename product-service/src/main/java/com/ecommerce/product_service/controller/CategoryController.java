package com.ecommerce.product_service.controller;

import com.ecommerce.product_service.dto.CategoryResponse;
import com.ecommerce.product_service.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        log.debug("GET /categories - Fetching all categories");

        List<CategoryResponse> categories = categoryService.getAll();
        return ResponseEntity.ok(categories);
    }
}
