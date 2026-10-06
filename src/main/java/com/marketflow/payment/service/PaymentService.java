package com.marketflow.payment.service;

import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderStatus;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.payment.domain.Payment;
import com.marketflow.payment.domain.PaymentStatus;
import com.marketflow.payment.dto.PaymentPrepareResponse;
import com.marketflow.payment.dto.PaymentResponse;
import com.marketflow.payment.exception.PaymentConflictException;
import com.marketflow.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public record Preparation(PaymentPrepareResponse payment, boolean created) {
    }

    @Transactional
    public Preparation prepare(Long userId, Long orderId) {
        Order order = orderRepository.findByUserIdAndIdForUpdate(userId, orderId)
                .orElseThrow(() -> new NoSuchElementException("주문을 찾을 수 없습니다."));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw PaymentConflictException.orderNotPayable();
        }

        // 주문 락 획득 후 이력을 읽어 동시 준비와 주문 취소를 직렬화한다.
        List<Payment> payments = paymentRepository.findAllByOrderIdOrderByCreatedAtAsc(orderId);
        List<Payment> active = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.READY || p.getStatus() == PaymentStatus.IN_PROGRESS)
                .toList();
        if (active.size() > 1) {
            throw PaymentConflictException.stateConflict();
        }
        if (payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.DONE
                || p.getStatus() == PaymentStatus.CANCELED)) {
            throw PaymentConflictException.orderNotPayable();
        }
        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            throw PaymentConflictException.orderNotPayable();
        }
        if (!active.isEmpty()) {
            Payment payment = active.getFirst();
            if (!payment.getAmount().equals(order.getTotalAmount())) {
                throw PaymentConflictException.stateConflict();
            }
            return new Preparation(PaymentPrepareResponse.from(payment), false);
        }

        Payment payment = paymentRepository.save(new Payment(order, UUID.randomUUID().toString(), order.getTotalAmount()));
        return new Preparation(PaymentPrepareResponse.from(payment), true);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByIdAndOrderUserId(paymentId, userId)
                .orElseThrow(() -> new NoSuchElementException("결제를 찾을 수 없습니다."));
        return PaymentResponse.from(payment);
    }
}
