package com.marketflow.product.repository;

import com.marketflow.product.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    List<ProductOption> findByProductIdAndActiveTrueOrderByIdAsc(Long productId);

    Optional<ProductOption> findByIdAndActiveTrue(Long id);
}
