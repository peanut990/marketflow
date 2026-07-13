package com.marketflow.product.dto;

import com.marketflow.product.domain.Product;

import java.time.LocalDateTime;

public record ProductSummaryResponse(
        Long id,
        String name,
        String description,
        String category,
        String thumbnailUrl,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getThumbnailUrl(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
