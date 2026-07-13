package com.marketflow.common.exception;

public record ErrorResponse(
        String code,
        String message
) {
}
