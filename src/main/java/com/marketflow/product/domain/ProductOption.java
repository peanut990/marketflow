package com.marketflow.product.domain;

import com.marketflow.common.entity.BaseEntity;
import com.marketflow.product.exception.InsufficientStockException;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "product_options",
        indexes = {
                @Index(name = "idx_product_options_product_id", columnList = "product_id"),
                @Index(name = "idx_product_options_product_active", columnList = "product_id, active")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private boolean active;

    public ProductOption(Product product, String name, Long price, int stockQuantity) {
        this.product = product;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.active = true;
    }

    public void decreaseStock(int quantity) {
        validatePositiveQuantity(quantity);

        if (this.stockQuantity < quantity) {
            throw new InsufficientStockException();
        }

        this.stockQuantity -= quantity;
    }

    public void increaseStock(int quantity) {
        validatePositiveQuantity(quantity);
        this.stockQuantity += quantity;
    }

    public void deactivate() {
        this.active = false;
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }
}
