package com.marketflow.order.dto;

import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderStatus;

import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long orderId,
        String orderNo,
        OrderStatus status,
        Long totalAmount,
        LocalDateTime orderedAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderedAt()
        );
    }
}
