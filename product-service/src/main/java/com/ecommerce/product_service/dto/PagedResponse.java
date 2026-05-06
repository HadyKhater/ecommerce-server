package com.ecommerce.product_service.dto;

import java.io.Serializable;
import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public PagedResponse {
        if (content == null) {
            content = List.of();
        }
    }
}
