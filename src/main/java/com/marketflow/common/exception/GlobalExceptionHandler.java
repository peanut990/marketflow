package com.marketflow.common.exception;

import com.marketflow.product.exception.InactiveProductOptionException;
import com.marketflow.product.exception.InsufficientStockException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoSuchElementException(NoSuchElementException exception) {
        return new ErrorResponse("NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgumentException(IllegalArgumentException exception) {
        return new ErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalStateException(IllegalStateException exception) {
        return new ErrorResponse("BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(InactiveProductOptionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInactiveProductOptionException(InactiveProductOptionException exception) {
        return new ErrorResponse("INACTIVE_PRODUCT_OPTION", exception.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInsufficientStockException(InsufficientStockException exception) {
        return new ErrorResponse("INSUFFICIENT_STOCK", exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLockingFailureException(OptimisticLockingFailureException exception) {
        return new ErrorResponse("CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLockException(OptimisticLockException exception) {
        return new ErrorResponse("CONFLICT", exception.getMessage());
    }
}
