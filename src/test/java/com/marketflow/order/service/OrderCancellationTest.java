package com.marketflow.order.service;

import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderItem;
import com.marketflow.order.repository.OrderItemRepository;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.payment.exception.PaymentConflictException;
import com.marketflow.payment.repository.PaymentRepository;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCancellationTest {

    @Test
    void 재고복구_결과가_0건이면_상태충돌_예외를_던진다() {
        Long userId = 1L;
        Long orderId = 2L;
        Long productOptionId = 3L;
        int quantity = 1;

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        ProductOptionRepository productOptionRepository = mock(ProductOptionRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderService orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                mock(CartItemRepository.class),
                productOptionRepository,
                mock(UserRepository.class),
                paymentRepository
        );

        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        ProductOption productOption = mock(ProductOption.class);
        when(orderRepository.findByUserIdAndIdForUpdate(userId, orderId)).thenReturn(Optional.of(order));
        when(order.getId()).thenReturn(orderId);
        when(paymentRepository.findAllByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());
        when(orderItemRepository.findByOrderIdOrderByIdAsc(orderId)).thenReturn(List.of(orderItem));
        when(orderItem.getProductOption()).thenReturn(productOption);
        when(productOption.getId()).thenReturn(productOptionId);
        when(orderItem.getQuantity()).thenReturn(quantity);
        when(productOptionRepository.increaseStockAtomically(productOptionId, quantity)).thenReturn(0);

        assertThatExceptionOfType(PaymentConflictException.class)
                .isThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .satisfies(exception -> assertThat(exception.getCode()).isEqualTo("PAYMENT_STATE_CONFLICT"));
        verify(productOptionRepository).increaseStockAtomically(productOptionId, quantity);
    }
}
