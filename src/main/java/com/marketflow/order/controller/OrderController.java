package com.marketflow.order.controller;

import com.marketflow.order.dto.OrderCreateRequest;
import com.marketflow.order.dto.OrderCreateResponse;
import com.marketflow.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreateResponse createOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody OrderCreateRequest request
    ) {
        return orderService.createOrder(userId, request);
    }
}
