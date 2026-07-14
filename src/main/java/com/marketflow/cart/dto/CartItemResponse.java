package com.marketflow.cart.dto;

import com.marketflow.cart.domain.CartItem;
import com.marketflow.product.domain.ProductOption;

public record CartItemResponse(
        Long cartItemId,
        Long productOptionId,
        String productName,
        String optionName,
        Long price,
        int quantity,
        Long totalPrice
) {

    public static CartItemResponse from(CartItem cartItem) {
        ProductOption productOption = cartItem.getProductOption();
        Long price = productOption.getPrice();
        int quantity = cartItem.getQuantity();

        return new CartItemResponse(
                cartItem.getId(),
                productOption.getId(),
                productOption.getProduct().getName(),
                productOption.getName(),
                price,
                quantity,
                price * quantity
        );
    }
}
