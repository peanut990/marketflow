package com.marketflow.payment.controller;

import com.marketflow.payment.domain.PaymentStatus;
import com.marketflow.payment.dto.PaymentPrepareResponse;
import com.marketflow.payment.dto.PaymentResponse;
import com.marketflow.payment.service.PaymentService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/orders/{orderId}/payments")
    public ResponseEntity<PaymentPrepareResponse> prepare(
            @RequestHeader("X-USER-ID") @Positive Long userId,
            @PathVariable @Positive Long orderId
    ) {
        PaymentService.Preparation result = paymentService.prepare(userId, orderId);
        HttpStatus status = result.created() ? HttpStatus.CREATED
                : result.payment().status() == PaymentStatus.IN_PROGRESS ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).location(URI.create(result.payment().statusUrl())).body(result.payment());
    }

    @GetMapping("/api/payments/{paymentId}")
    public PaymentResponse getPayment(
            @RequestHeader("X-USER-ID") @Positive Long userId,
            @PathVariable @Positive Long paymentId
    ) {
        return paymentService.getPayment(userId, paymentId);
    }
}
