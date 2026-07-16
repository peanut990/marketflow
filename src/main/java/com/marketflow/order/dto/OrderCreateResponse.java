package com.marketflow.order.dto;

import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderItem;
import com.marketflow.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCreateResponse(
        Long orderId,
        String orderNo,
        OrderStatus status,
        Long totalAmount,
        LocalDateTime orderedAt,
        List<OrderItemResponse> items
) {

    public static OrderCreateResponse of(Order order, List<OrderItem> orderItems) {
        return new OrderCreateResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderedAt(),
                orderItems.stream()
                        .map(OrderItemResponse::from)
                        .toList()
        );
    }
}
