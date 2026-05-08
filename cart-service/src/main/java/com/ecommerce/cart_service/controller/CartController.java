package com.ecommerce.cart_service.controller;

import com.ecommerce.cart_service.dto.AddToCartRequest;
import com.ecommerce.cart_service.dto.CartResponse;
import com.ecommerce.cart_service.dto.UpdateItemRequest;
import com.ecommerce.cart_service.service.ICartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Cart REST controller.
 * All endpoints trust X-User-Email header forwarded by API Gateway.
 * No JWT parsing in this service - Gateway handles authentication.
 *
 * Endpoints:
 * GET /cart - Get current user's cart with total
 * POST /cart/items - Add item (body: productId, quantity, optional unitPrice)
 * PUT /cart/items/{id} - Update quantity (body: quantity)
 * DELETE /cart/items/{id} - Remove single item
 * DELETE /cart - Clear entire cart
 */
@Slf4j
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private static final String USER_EMAIL_HEADER = "X-User-Email";

    private final ICartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail) {
        log.debug("GET /cart - Getting cart for user: {}", userEmail);

        CartResponse cart = cartService.getCart(userEmail);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @Valid @RequestBody AddToCartRequest request) {
        log.info("POST /cart/items - User: {} adding product: {} with quantity: {}",
                userEmail, request.productId(), request.quantity());

        CartResponse updatedCart = cartService.addToCart(userEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(updatedCart);
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateItemRequest request) {
        log.info("PUT /cart/items/{} - User: {} updating quantity to: {}",
                productId, userEmail, request.quantity());

        CartResponse updatedCart = cartService.updateQuantity(userEmail, productId, request);
        return ResponseEntity.ok(updatedCart);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail,
            @PathVariable UUID productId) {
        log.info("DELETE /cart/items/{} - User: {} removing product from cart",
                productId, userEmail);

        CartResponse updatedCart = cartService.removeFromCart(userEmail, productId);
        return ResponseEntity.ok(updatedCart);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart(
            @RequestHeader(USER_EMAIL_HEADER) String userEmail) {
        log.info("DELETE /cart/clear - User: {} clearing cart", userEmail);
        cartService.clearCart(userEmail);
        return ResponseEntity.noContent().build();
    }
}
