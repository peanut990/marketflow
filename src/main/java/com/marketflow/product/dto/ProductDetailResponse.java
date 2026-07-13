package com.marketflow.product.dto;

import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;

import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        String category,
        String thumbnailUrl,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ProductOptionResponse> options
) {

    public static ProductDetailResponse of(Product product, List<ProductOption> productOptions) {
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getThumbnailUrl(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                productOptions.stream()
                        .map(ProductOptionResponse::from)
                        .toList()
        );
    }
}
