package com.marketflow.product.domain;

import com.marketflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_products_category_active", columnList = "category, active"),
                @Index(name = "idx_products_active_created_at", columnList = "active, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 500)
    private String thumbnailUrl;

    @Column(nullable = false)
    private boolean active;

    public Product(String name, String description, String category, String thumbnailUrl) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.thumbnailUrl = thumbnailUrl;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
