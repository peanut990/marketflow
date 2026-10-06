package com.marketflow.common.exception;

import com.marketflow.order.controller.OrderController;
import com.marketflow.order.service.OrderService;
import com.marketflow.payment.controller.PaymentController;
import com.marketflow.payment.controller.PaymentExceptionHandler;
import com.marketflow.payment.service.PaymentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new OrderController(orderService), new PaymentController(paymentService))
            .setControllerAdvice(new GlobalExceptionHandler(), new PaymentExceptionHandler())
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders/1", "/api/payments/1"})
    void 필수_헤더_누락은_공통_입력오류로_응답한다(String path) throws Exception {
        assertBadRequest(mockMvc.perform(get(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders/abc", "/api/payments/abc"})
    void 경로_ID의_타입변환_실패는_공통_입력오류로_응답한다(String path) throws Exception {
        assertBadRequest(mockMvc.perform(get(path).header("X-USER-ID", 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders/1", "/api/payments/1"})
    void 헤더_ID의_타입변환_실패는_공통_입력오류로_응답한다(String path) throws Exception {
        assertBadRequest(mockMvc.perform(get(path).header("X-USER-ID", "abc")));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 결제_ID의_양수검증_실패는_공통_입력오류로_응답한다(long paymentId) throws Exception {
        assertBadRequest(mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                .header("X-USER-ID", 1)));
    }

    private void assertBadRequest(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("필수 입력값과 입력 형식을 확인해 주세요."));
        verifyNoInteractions(orderService, paymentService);
    }
}
