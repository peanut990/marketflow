package com.marketflow.payment.domain;

import com.marketflow.common.entity.BaseEntity;
import com.marketflow.order.domain.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_pg_order_id", columnNames = "pg_order_id"),
                @UniqueConstraint(name = "uk_payments_payment_key", columnNames = "payment_key")
        },
        indexes = {
                @Index(name = "idx_payments_order_created_at", columnList = "order_id, created_at"),
                @Index(name = "idx_payments_status_requested_at", columnList = "status, requested_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "pg_order_id", nullable = false, length = 64)
    private String pgOrderId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 510)
    private String failureMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    public Payment(Order order, String pgOrderId, Long amount) {
        if (order == null) {
            throw new IllegalArgumentException("주문은 필수입니다.");
        }
        validateNotBlank(pgOrderId, "PG 주문 ID는 필수입니다.");
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("결제 금액은 양수여야 합니다.");
        }

        this.order = order;
        this.pgOrderId = pgOrderId;
        this.amount = amount;
        this.status = PaymentStatus.READY;
        this.requestedAt = LocalDateTime.now();
    }

    public void startApproval(String paymentKey) {
        validateStatus(PaymentStatus.READY);
        validateNotBlank(paymentKey, "결제 키는 필수입니다.");

        this.paymentKey = paymentKey;
        this.status = PaymentStatus.IN_PROGRESS;
    }

    public void complete(LocalDateTime approvedAt) {
        validateStatus(PaymentStatus.IN_PROGRESS);
        if (approvedAt == null) {
            throw new IllegalArgumentException("결제 승인 시각은 필수입니다.");
        }

        this.approvedAt = approvedAt;
        this.status = PaymentStatus.DONE;
    }

    public void fail(String failureCode, String failureMessage) {
        validateStatus(PaymentStatus.IN_PROGRESS);
        validateNotBlank(failureCode, "결제 실패 코드는 필수입니다.");
        validateNotBlank(failureMessage, "결제 실패 메시지는 필수입니다.");

        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.status = PaymentStatus.FAILED;
    }

    public void expire() {
        validateStatus(PaymentStatus.READY);
        this.status = PaymentStatus.EXPIRED;
    }

    public void cancel() {
        validateStatus(PaymentStatus.DONE);
        this.status = PaymentStatus.CANCELED;
    }

    private void validateStatus(PaymentStatus expectedStatus) {
        if (this.status != expectedStatus) {
            throw new IllegalStateException("현재 결제 상태에서는 처리할 수 없습니다.");
        }
    }

    private static void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
