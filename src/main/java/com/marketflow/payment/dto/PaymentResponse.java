package com.marketflow.payment.dto;

import com.marketflow.order.domain.OrderStatus;
import com.marketflow.payment.domain.Payment;
import com.marketflow.payment.domain.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId, Long orderId, String pgOrderId, Long amount, PaymentStatus status,
        OrderStatus orderStatus, String cancelStatus, LocalDateTime requestedAt,
        LocalDateTime approvedAt, String failureCode, String failureMessage, String statusUrl
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getPgOrderId(),
                payment.getAmount(), payment.getStatus(), payment.getOrder().getStatus(),
                payment.getStatus() == PaymentStatus.CANCELED ? "DONE" : "NONE",
                payment.getRequestedAt(), payment.getApprovedAt(), payment.getFailureCode(),
                payment.getFailureMessage(), "/api/payments/" + payment.getId());
    }
}
