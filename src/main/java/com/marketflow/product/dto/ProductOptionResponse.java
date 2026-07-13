package com.marketflow.product.dto;

import com.marketflow.product.domain.ProductOption;

public record ProductOptionResponse(
        Long id,
        String name,
        Long price,
        int stockQuantity,
        boolean active
) {

    public static ProductOptionResponse from(ProductOption productOption) {
        return new ProductOptionResponse(
                productOption.getId(),
                productOption.getName(),
                productOption.getPrice(),
                productOption.getStockQuantity(),
                productOption.isActive()
        );
    }
}
