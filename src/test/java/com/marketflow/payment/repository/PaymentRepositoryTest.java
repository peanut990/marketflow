package com.marketflow.payment.repository;

import com.marketflow.order.domain.Order;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.payment.domain.Payment;
import com.marketflow.support.IntegrationTest;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PaymentRepositoryTest extends IntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private Order order;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User(
                "payment-repository-test@marketflow.com",
                "password",
                "결제 저장소 테스트 사용자",
                "010-5555-5555"
        ));
        order = orderRepository.save(new Order("ORDER-PAYMENT-001", user, 10_000L));
    }

    @Test
    void pgOrderId로_결제_시도를_조회한다() {
        Payment savedPayment = paymentRepository.save(new Payment(order, "PG-ORDER-001", 10_000L));

        Payment foundPayment = paymentRepository.findByPgOrderId("PG-ORDER-001").orElseThrow();

        assertThat(foundPayment.getId()).isEqualTo(savedPayment.getId());
    }

    @Test
    void paymentKey로_결제_시도를_조회한다() {
        Payment payment = new Payment(order, "PG-ORDER-001", 10_000L);
        payment.startApproval("payment-key-001");
        paymentRepository.save(payment);

        Payment foundPayment = paymentRepository.findByPaymentKey("payment-key-001").orElseThrow();

        assertThat(foundPayment.getPgOrderId()).isEqualTo("PG-ORDER-001");
    }

    @Test
    void 한_주문에_여러_결제_시도를_저장하고_생성순으로_조회한다() {
        Payment firstPayment = paymentRepository.saveAndFlush(new Payment(order, "PG-ORDER-001", 10_000L));
        Payment secondPayment = paymentRepository.saveAndFlush(new Payment(order, "PG-ORDER-002", 10_000L));

        List<Payment> payments = paymentRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId());

        assertThat(payments)
                .extracting(Payment::getId)
                .containsExactly(firstPayment.getId(), secondPayment.getId());
    }

    @Test
    void pgOrderId는_중복해서_저장할_수_없다() {
        paymentRepository.saveAndFlush(new Payment(order, "PG-ORDER-001", 10_000L));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(new Payment(order, "PG-ORDER-001", 10_000L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void READY_상태의_null_paymentKey는_여러_건_저장할_수_있다() {
        paymentRepository.save(new Payment(order, "PG-ORDER-001", 10_000L));
        paymentRepository.save(new Payment(order, "PG-ORDER-002", 10_000L));

        assertThat(paymentRepository.findAll()).hasSize(2);
    }

    @Test
    void null이_아닌_paymentKey는_중복해서_저장할_수_없다() {
        Payment firstPayment = new Payment(order, "PG-ORDER-001", 10_000L);
        firstPayment.startApproval("payment-key-001");
        paymentRepository.saveAndFlush(firstPayment);
        Payment secondPayment = new Payment(order, "PG-ORDER-002", 10_000L);
        secondPayment.startApproval("payment-key-001");

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(secondPayment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
