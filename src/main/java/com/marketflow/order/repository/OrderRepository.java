package com.marketflow.order.repository;

import com.marketflow.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderNo(String orderNo);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Optional<Order> findByUserIdAndId(Long userId, Long id);
}
