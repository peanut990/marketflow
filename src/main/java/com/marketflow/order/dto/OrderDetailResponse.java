package com.marketflow.order.dto;

import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderItem;
import com.marketflow.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long orderId,
        String orderNo,
        OrderStatus status,
        Long totalAmount,
        LocalDateTime orderedAt,
        List<OrderItemResponse> items
) {

    public static OrderDetailResponse of(Order order, List<OrderItem> orderItems) {
        return new OrderDetailResponse(
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
