package com.marketflow.product.exception;

public class InactiveProductOptionException extends RuntimeException {

    public InactiveProductOptionException() {
        super("비활성 상품 옵션은 주문할 수 없습니다.");
    }
}
