package com.marketflow.product.repository;

import com.marketflow.product.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    List<ProductOption> findByProductIdAndActiveTrueOrderByIdAsc(Long productId);

    Optional<ProductOption> findByIdAndActiveTrue(Long id);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ProductOption productOption
            set productOption.stockQuantity = productOption.stockQuantity - :quantity
            where productOption.id = :id
                and productOption.active = true
                and productOption.stockQuantity >= :quantity
            """)
    int decreaseStockAtomically(@Param("id") Long id, @Param("quantity") int quantity);
}
