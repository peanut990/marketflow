package com.marketflow.payment.domain;

import com.marketflow.order.domain.Order;
import com.marketflow.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private Order order;

    @BeforeEach
    void setUp() {
        User user = new User("payment-test@marketflow.com", "password", "결제 테스트 사용자", "010-1111-1111");
        order = new Order("ORDER-001", user, 10_000L);
    }

    @Test
    void 결제_시도를_READY_상태로_생성한다() {
        Payment payment = new Payment(order, "PG-ORDER-001", 10_000L);

        assertThat(payment.getOrder()).isEqualTo(order);
        assertThat(payment.getPgOrderId()).isEqualTo("PG-ORDER-001");
        assertThat(payment.getAmount()).isEqualTo(10_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getRequestedAt()).isNotNull();
        assertThat(payment.getPaymentKey()).isNull();
    }

    @Test
    void 필수값이_없거나_결제_금액이_양수가_아니면_생성할_수_없다() {
        assertThatThrownBy(() -> new Payment(null, "PG-ORDER-001", 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Payment(order, " ", 10_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Payment(order, "PG-ORDER-001", 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Payment(order, "PG-ORDER-001", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 결제_인증에_성공하면_paymentKey를_저장하고_IN_PROGRESS로_변경한다() {
        Payment payment = createPayment();

        payment.startApproval("payment-key-001");

        assertThat(payment.getPaymentKey()).isEqualTo("payment-key-001");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    void 결제_승인이_완료되면_DONE으로_변경한다() {
        Payment payment = createInProgressPayment();
        LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 16, 12, 0);

        payment.complete(approvedAt);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    void 결제_승인이_실패하면_실패_정보를_저장하고_FAILED로_변경한다() {
        Payment payment = createInProgressPayment();

        payment.fail("REJECT_CARD_COMPANY", "카드사에서 결제를 거절했습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(payment.getFailureMessage()).isEqualTo("카드사에서 결제를 거절했습니다.");
    }

    @Test
    void 결제_요청이_만료되면_EXPIRED로_변경한다() {
        Payment payment = createPayment();

        payment.expire();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void 완료된_결제를_취소하면_CANCELED로_변경한다() {
        Payment payment = createInProgressPayment();
        payment.complete(LocalDateTime.now());

        payment.cancel();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void 허용되지_않은_상태에서는_결제_상태를_변경할_수_없다() {
        Payment readyPayment = createPayment();
        Payment inProgressPayment = createInProgressPayment();

        assertThatThrownBy(() -> readyPayment.complete(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(readyPayment::cancel)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(inProgressPayment::expire)
                .isInstanceOf(IllegalStateException.class);
    }

    private Payment createPayment() {
        return new Payment(order, "PG-ORDER-001", 10_000L);
    }

    private Payment createInProgressPayment() {
        Payment payment = createPayment();
        payment.startApproval("payment-key-001");
        return payment;
    }
}
