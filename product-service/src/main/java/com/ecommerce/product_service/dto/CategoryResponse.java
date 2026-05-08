package com.ecommerce.product_service.dto;

import java.io.Serializable;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        boolean active
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
