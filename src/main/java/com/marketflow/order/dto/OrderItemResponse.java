package com.marketflow.order.dto;

import com.marketflow.order.domain.OrderItem;

public record OrderItemResponse(
        Long orderItemId,
        Long productOptionId,
        String productName,
        String optionName,
        Long orderPrice,
        int quantity,
        Long totalPrice
) {

    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getProductOption().getId(),
                orderItem.getProductName(),
                orderItem.getOptionName(),
                orderItem.getOrderPrice(),
                orderItem.getQuantity(),
                orderItem.getTotalPrice()
        );
    }
}
