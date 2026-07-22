package com.marketflow.order.repository;

import com.marketflow.order.domain.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = "productOption")
    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);
}
