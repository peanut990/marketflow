package com.marketflow.payment.controller;

import com.marketflow.common.exception.GlobalExceptionHandler;
import com.marketflow.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentExceptionHandlerTest {

    private final PaymentService service = mock(PaymentService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
            .setControllerAdvice(new PaymentExceptionHandler(), new GlobalExceptionHandler()).build();

    @Test
    void 서비스_커밋_실패는_503으로_응답한다() throws Exception {
        when(service.prepare(1L, 2L)).thenThrow(new TransactionSystemException("commit failed"));
        mvc.perform(post("/api/orders/2/payments").header("X-USER-ID", 1))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_RESULT_UNAVAILABLE"));
    }

    @Test
    void 조회_DB_장애는_503으로_응답한다() throws Exception {
        when(service.getPayment(1L, 2L)).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        mvc.perform(get("/api/payments/2").header("X-USER-ID", 1))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_RESULT_UNAVAILABLE"));
    }

    @Test
    void 프로그래밍_오류는_DB_장애로_숨기지_않는다() {
        when(service.prepare(1L, 2L)).thenThrow(new NullPointerException("bug"));
        assertThatThrownBy(() -> mvc.perform(post("/api/orders/2/payments").header("X-USER-ID", 1)))
                .hasRootCauseInstanceOf(NullPointerException.class);
    }
}
