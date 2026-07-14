package com.marketflow.cart.dto;

public record CartItemCreateRequest(
        Long productOptionId,
        Integer quantity
) {
}
