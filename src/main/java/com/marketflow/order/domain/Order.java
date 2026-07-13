package com.marketflow.order.domain;

import com.marketflow.common.entity.BaseEntity;
import com.marketflow.user.domain.User;
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
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_order_no", columnNames = "order_no")
        },
        indexes = {
                @Index(name = "idx_orders_user_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_orders_status", columnList = "status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 50)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    public Order(String orderNo, User user, Long totalAmount) {
        this.orderNo = orderNo;
        this.user = user;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.CREATED;
        this.orderedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("취소할 수 없는 주문 상태입니다.");
        }

        this.status = OrderStatus.CANCELED;
    }
}
