package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.CategoryRequest;
import com.ecommerce.product_service.dto.CategoryResponse;
import com.ecommerce.product_service.entity.Category;
import com.ecommerce.product_service.exception.CategoryNotFoundException;
import com.ecommerce.product_service.repository.CategoryRepository;
import com.ecommerce.product_service.repository.ProductRepository;
import com.ecommerce.product_service.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService implements ICategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        log.debug("Fetching all categories");

        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(UUID id) {
        log.debug("Fetching category by id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        return mapToResponse(category);
    }

    @Override
    public CategoryResponse getCategoryBySlug(String slug) {
        log.debug("Fetching category by slug: {}", slug);

        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with slug: " + slug));

        return mapToResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        log.debug("Creating category with name: {}", request.name());

        if (categoryRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Category name already exists");
        }

        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        category.setActive(request.active() != null ? request.active() : true);

        // Generate unique slug from name
        String baseSlug = SlugUtil.generateSlug(request.name());
        String uniqueSlug = SlugUtil.generateUniqueSlug(baseSlug, categoryRepository::existsBySlug);
        category.setSlug(uniqueSlug);

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created with id: {} and slug: {}", savedCategory.getId(), savedCategory.getSlug());

        return mapToResponse(savedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        log.debug("Updating category with id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Category name already exists");
        }

        // Regenerate slug if name has changed
        if (!category.getName().equals(request.name())) {
            String baseSlug = SlugUtil.generateSlug(request.name());
            String uniqueSlug = SlugUtil.generateUniqueSlug(baseSlug, slug ->
                    categoryRepository.existsBySlug(slug) && !slug.equals(category.getSlug()));
            category.setSlug(uniqueSlug);
        }

        category.setName(request.name());
        category.setDescription(request.description());
        if (request.active() != null) category.setActive(request.active());

        Category savedCategory = categoryRepository.save(category);
        log.info("Category updated with id: {} and slug: {}", savedCategory.getId(), savedCategory.getSlug());

        return mapToResponse(savedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        log.debug("Deleting category with id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        boolean hasProducts = productRepository.existsByCategoryIdAndDeletedFalse(id);
        if (hasProducts) {
            throw new IllegalArgumentException("Cannot delete category with existing products");
        }

        categoryRepository.delete(category);
        log.info("Category deleted with id: {}", id);
    }

    private CategoryResponse mapToResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.isActive()
        );
    }

    @Override
    @Transactional
    public CategoryResponse toggleActive(UUID id) {
        log.debug("Toggling active status for category with id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        category.setActive(!category.isActive());
        Category savedCategory = categoryRepository.save(category);
        log.info("Category active status toggled to {} for id: {}", savedCategory.isActive(), id);

        return mapToResponse(savedCategory);
    }
}
