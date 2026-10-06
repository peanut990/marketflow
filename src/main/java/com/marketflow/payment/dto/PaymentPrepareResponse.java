package com.marketflow.payment.dto;

import com.marketflow.payment.domain.Payment;
import com.marketflow.payment.domain.PaymentStatus;

public record PaymentPrepareResponse(
        Long paymentId, String pgOrderId, Long amount, PaymentStatus status, String statusUrl
) {
    public static PaymentPrepareResponse from(Payment payment) {
        return new PaymentPrepareResponse(payment.getId(), payment.getPgOrderId(), payment.getAmount(),
                payment.getStatus(), "/api/payments/" + payment.getId());
    }
}
