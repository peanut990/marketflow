package com.marketflow.payment.controller;

import com.marketflow.common.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(0)
@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentExceptionHandler {

    // 서비스 프록시의 커밋 실패도 컨트롤러 경계에서 처리한다.
    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class,
            TransactionSystemException.class, UnexpectedRollbackException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleDatabaseFailure(Exception exception) {
        log.error("결제 데이터 처리 결과를 확인할 수 없습니다.", exception);
        return new ErrorResponse("PAYMENT_RESULT_UNAVAILABLE", "결제 결과를 확인할 수 없습니다. 동일 요청으로 다시 확인해 주세요.");
    }
}
