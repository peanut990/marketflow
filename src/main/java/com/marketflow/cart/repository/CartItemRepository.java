package com.marketflow.cart.repository;

import com.marketflow.cart.domain.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {"productOption", "productOption.product"})
    List<CartItem> findByUserIdOrderByIdAsc(Long userId);

    @EntityGraph(attributePaths = {"productOption", "productOption.product"})
    Optional<CartItem> findByUserIdAndId(Long userId, Long id);

    @EntityGraph(attributePaths = {"productOption", "productOption.product"})
    List<CartItem> findByUserIdAndIdInOrderByIdAsc(Long userId, List<Long> ids);

    Optional<CartItem> findByUserIdAndProductOptionId(Long userId, Long productOptionId);
}
