package com.ecommerce.cart_service.service;

import com.ecommerce.cart_service.client.ProductClient;
import com.ecommerce.cart_service.dto.AddToCartRequest;
import com.ecommerce.cart_service.dto.CartItemResponse;
import com.ecommerce.cart_service.dto.CartResponse;
import com.ecommerce.cart_service.dto.UpdateItemRequest;
import com.ecommerce.cart_service.entity.Cart;
import com.ecommerce.cart_service.entity.CartItem;
import com.ecommerce.cart_service.exception.CartItemNotFoundException;
import com.ecommerce.cart_service.exception.CartNotFoundException;
import com.ecommerce.cart_service.exception.ProductNotFoundException;
import com.ecommerce.cart_service.repository.CartItemRepository;
import com.ecommerce.cart_service.repository.CartRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cart service implementation.
 * Handles all cart operations with optimistic locking and caching.
 * User identification via X-User-Email header (no JWT parsing).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService implements ICartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductClient productClient;

    @Override
    @Cacheable(value = "carts", key = "#userEmail")
    public CartResponse getCart(String userEmail) {
        log.debug("Getting cart for user: {}", userEmail);

        Cart cart = findOrCreateCart(userEmail);
        return mapToResponse(cart);
    }

    @Override
    @Transactional
    @CacheEvict(value = "carts", key = "#userEmail")
    public CartResponse addToCart(String userEmail, AddToCartRequest request) {
        log.info("Adding product {} with quantity {} to cart for user: {}",
                request.productId(), request.quantity(), userEmail);

        // Validate product exists and get current price
        ProductClient.ProductDTO product = validateAndGetProduct(request.productId());

        // Use provided unit price or fall back to current product price
        BigDecimal unitPrice = request.unitPrice() != null
                ? request.unitPrice()
                : product.price();

        Cart cart = findOrCreateCart(userEmail);

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.productId());

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.quantity());
            cartItemRepository.save(item);
            log.info("Updated quantity for existing cart item. New quantity: {}", item.getQuantity());
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProductId(request.productId());
            newItem.setQuantity(request.quantity());
            newItem.setUnitPrice(unitPrice);
            cart.addItem(newItem);
            log.info("Added new item to cart with unit price: {}", unitPrice);
        }

        Cart savedCart = cartRepository.save(cart);
        return mapToResponse(savedCart);
    }

    @Override
    @Transactional
    @CacheEvict(value = "carts", key = "#userEmail")
    public CartResponse updateQuantity(String userEmail, UUID productId, UpdateItemRequest request) {
        log.info("Updating quantity for product {} to {} for user: {}",
                productId, request.quantity(), userEmail);

        Cart cart = cartRepository.findByUserEmail(userEmail)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userEmail));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));

        int newQuantity = request.quantity();
        if (newQuantity <= 0) {
            // Remove item when quantity is 0 or less
            cart.removeItem(item);
            cartItemRepository.delete(item);
            cartRepository.save(cart);
            log.info("Removed product {} from cart due to quantity <= 0", productId);
        } else {
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
            cartRepository.save(cart);
            log.info("Updated quantity for product {} to {}", productId, newQuantity);
        }

        return mapToResponse(cart);
    }

    @Override
    @Transactional
    @CacheEvict(value = "carts", key = "#userEmail")
    public CartResponse removeFromCart(String userEmail, UUID productId) {
        log.info("Removing product {} from cart for user: {}", productId, userEmail);

        Cart cart = cartRepository.findByUserEmail(userEmail)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userEmail));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));

        cart.removeItem(item);
        cartItemRepository.delete(item);

        Cart savedCart = cartRepository.save(cart);
        log.info("Removed product {} from cart", productId);

        return mapToResponse(savedCart);
    }

    @Override
    @Transactional
    @CacheEvict(value = "carts", key = "#userEmail")
    public void clearCart(String userEmail) {
        log.info("Clearing cart for user: {}", userEmail);

        Optional<Cart> cartOpt = cartRepository.findByUserEmail(userEmail);
        if (cartOpt.isEmpty()) {
            log.debug("No cart found for user: {}, nothing to clear", userEmail);
            return;
        }

        Cart cart = cartOpt.get();
        if (!cart.getItems().isEmpty()) {
            // Explicitly delete all items for the cart
            cartItemRepository.deleteAllByCartId(cart.getId());
            cart.getItems().clear();
            cartRepository.save(cart);
        }

        log.info("Cleared cart for user: {}", userEmail);
    }

    private Cart findOrCreateCart(String userEmail) {
        return cartRepository.findByUserEmail(userEmail)
                .orElseGet(() -> {
                    log.info("Creating new cart for user: {}", userEmail);
                    Cart newCart = new Cart();
                    newCart.setUserEmail(userEmail);
                    newCart.setItems(new ArrayList<>());
                    return cartRepository.save(newCart);
                });
    }

    private ProductClient.ProductDTO validateAndGetProduct(UUID productId) {
        try {
            return productClient.getProduct(productId);
        } catch (Exception e) {

            log.error("Product not found or service unavailable: {}", productId);

            throw new ProductNotFoundException(productId);
        }
    }

    private CartResponse mapToResponse(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        int totalItemsCount = 0;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            try {
                // Try to fetch current product details for name and image
                ProductClient.ProductDTO product = null;
                try {
                    product = productClient.getProduct(item.getProductId());
                } catch (Exception e) {
                    log.warn("Could not fetch product details for productId: {}", item.getProductId());
                }

                String productName = product != null ? product.name() : "Unknown Product";
                String imageUrl = product != null && product.imageUrls() != null && !product.imageUrls().isEmpty()
                        ? product.imageUrls().get(0)
                        : null;

                // Use the stored unit price snapshot
                BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                grandTotal = grandTotal.add(itemTotal);
                totalItemsCount += item.getQuantity();

                CartItemResponse itemResponse = new CartItemResponse(
                        item.getProductId(),
                        productName,
                        item.getUnitPrice(),
                        imageUrl,
                        item.getQuantity(),
                        itemTotal
                );
                itemResponses.add(itemResponse);
            } catch (Exception e) {
                log.warn("Error processing cart item for productId: {}. Error: {}",
                        item.getProductId(), e.getMessage());
            }
        }

        return new CartResponse(
                cart.getId(),
                cart.getUserEmail(),
                itemResponses,
                totalItemsCount,
                grandTotal,
                cart.getCreatedAt(),
                cart.getUpdatedAt()
        );
    }
}
