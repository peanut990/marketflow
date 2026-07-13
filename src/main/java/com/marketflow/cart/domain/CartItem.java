package com.marketflow.cart.domain;

import com.marketflow.common.entity.BaseEntity;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cart_items_user_option",
                        columnNames = {"user_id", "product_option_id"}
                )
        },
        indexes = {
                @Index(name = "idx_cart_items_user_id", columnList = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id", nullable = false)
    private ProductOption productOption;

    @Column(nullable = false)
    private int quantity;

    public CartItem(User user, ProductOption productOption, int quantity) {
        validatePositiveQuantity(quantity);
        this.user = user;
        this.productOption = productOption;
        this.quantity = quantity;
    }

    public void addQuantity(int quantity) {
        validatePositiveQuantity(quantity);
        this.quantity += quantity;
    }

    public void changeQuantity(int quantity) {
        validatePositiveQuantity(quantity);
        this.quantity = quantity;
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }
}
