package com.ecommerce.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeCheckoutRequest {

    @NotNull(message = "Order ID is required")
    private UUID orderId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotEmpty(message = "At least one item is required")
    private List<LineItem> items;

    private String discountCode;

    private BigDecimal discountAmount;

    private String discountType;

    private BigDecimal discountValue;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        @NotBlank(message = "Item name is required")
        private String name;

        private String description;

        @NotNull(message = "Item amount is required")
        private BigDecimal amount;

        @NotBlank(message = "Item currency is required")
        private String currency;

        @NotNull(message = "Item quantity is required")
        @DecimalMin(value = "1", message = "Quantity must be at least 1")
        private Long quantity;
    }
}