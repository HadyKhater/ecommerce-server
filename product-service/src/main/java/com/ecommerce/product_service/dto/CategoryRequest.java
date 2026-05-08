package com.ecommerce.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 500)
        String description,

        Boolean active
) {
}
