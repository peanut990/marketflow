package com.marketflow.order.controller;

import com.marketflow.order.dto.OrderCreateRequest;
import com.marketflow.order.dto.OrderCreateResponse;
import com.marketflow.order.dto.OrderDetailResponse;
import com.marketflow.order.dto.OrderSummaryResponse;
import com.marketflow.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public Page<OrderSummaryResponse> getOrders(
            @RequestHeader("X-USER-ID") Long userId,
            @PageableDefault(size = 20)
            @SortDefault(sort = "orderedAt", direction = DESC)
            Pageable pageable
    ) {
        return orderService.getOrders(userId, pageable);
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable Long orderId
    ) {
        return orderService.getOrder(userId, orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreateResponse createOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody OrderCreateRequest request
    ) {
        return orderService.createOrder(userId, request);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderDetailResponse cancelOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable Long orderId
    ) {
        return orderService.cancelOrder(userId, orderId);
    }
}
