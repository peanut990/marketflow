## **패키지 구조**

```
com.marketflow
├── MarketflowApplication
├── common
│   └── entity
│       └── BaseEntity
├── user
│   └── domain
│       ├── User
│       └── UserRole
├── product
│   └── domain
│       ├── Product
│       └── ProductOption
├── cart
│   └── domain
│       └── CartItem
└── order
    └── domain
        ├── Order
        ├── OrderItem
        └── OrderStatus
```

---

# **엔티티 코드**

## **BaseEntity**

```java
package com.marketflow.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

`MarketflowApplication`에 추가:

```java
@EnableJpaAuditing
@SpringBootApplication
public class MarketflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketflowApplication.class, args);
    }
}
```

---

## **UserRole**

```java
package com.marketflow.user.domain;

public enum UserRole {
    USER,
    ADMIN
}
```

---

## **User**

```java
package com.marketflow.user.domain;

import com.marketflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    public User(String email, String password, String name, String phone) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.role = UserRole.USER;
    }
}
```

---

## **Product**

```java
package com.marketflow.product.domain;

import com.marketflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_products_category_active", columnList = "category, active"),
                @Index(name = "idx_products_active_created_at", columnList = "active, createdAt")
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
```

---

## **ProductOption**

```java
package com.marketflow.product.domain;

import com.marketflow.common.entity.BaseEntity;
import jakarta.persistence.*;
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

    @Version
    private Long version;

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
            throw new IllegalArgumentException("재고가 부족합니다.");
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
```

여기서 예외는 나중에 `OutOfStockException`, `InvalidQuantityException`으로 바꾸자. 지금은 엔티티 컴파일 우선.

---

## **CartItem**

```java
package com.marketflow.cart.domain;

import com.marketflow.common.entity.BaseEntity;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.user.domain.User;
import jakarta.persistence.*;
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
```

---

## **OrderStatus**

```java
package com.marketflow.order.domain;

public enum OrderStatus {
    CREATED,
    CANCELED
}
```

---

## **Order**

```java
package com.marketflow.order.domain;

import com.marketflow.common.entity.BaseEntity;
import com.marketflow.user.domain.User;
import jakarta.persistence.*;
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
                @Index(name = "idx_orders_user_created_at", columnList = "user_id, createdAt"),
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
```

---

## **OrderItem**

```java
package com.marketflow.order.domain;

import com.marketflow.product.domain.ProductOption;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order_id", columnList = "order_id"),
                @Index(name = "idx_order_items_product_option_id", columnList = "product_option_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id", nullable = false)
    private ProductOption productOption;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false, length = 100)
    private String optionName;

    @Column(nullable = false)
    private Long orderPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private Long totalPrice;

    public OrderItem(Order order, ProductOption productOption, int quantity) {
        validatePositiveQuantity(quantity);

        this.order = order;
        this.productOption = productOption;
        this.productName = productOption.getProduct().getName();
        this.optionName = productOption.getName();
        this.orderPrice = productOption.getPrice();
        this.quantity = quantity;
        this.totalPrice = productOption.getPrice() * quantity;
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }
}
```

---

## **지금 코드에서 일부러 안 넣은 것**

```
@OneToMany 양방향 연관관계
cascade
orphanRemoval
payments
coupons
stocks
Spring Security
```

특히 `Order -> List<OrderItem>` 양방향은 나중에 필요하면 넣자. 지금 넣으면 편해 보이지만, 연관관계 편의 메서드/영속성 전이까지 신경 쓸 게 늘어난다.
