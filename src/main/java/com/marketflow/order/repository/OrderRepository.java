package com.marketflow.order.repository;

import com.marketflow.order.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderNo(String orderNo);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Optional<Order> findByUserIdAndId(Long userId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.user.id = :userId and o.id = :id")
    Optional<Order> findByUserIdAndIdForUpdate(@Param("userId") Long userId, @Param("id") Long id);
}
