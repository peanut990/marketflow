package com.marketflow.payment.exception;

import lombok.Getter;

@Getter
public class PaymentConflictException extends RuntimeException {

    private final String code;

    private PaymentConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static PaymentConflictException orderNotPayable() {
        return new PaymentConflictException("ORDER_NOT_PAYABLE", "결제할 수 없는 주문 상태입니다.");
    }

    public static PaymentConflictException stateConflict() {
        return new PaymentConflictException("PAYMENT_STATE_CONFLICT", "현재 결제 상태에서는 처리할 수 없습니다.");
    }
}
