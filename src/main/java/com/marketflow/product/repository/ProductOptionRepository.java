package com.marketflow.product.repository;

import com.marketflow.product.domain.ProductOption;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    List<ProductOption> findByProductIdAndActiveTrueOrderByIdAsc(Long productId);

    Optional<ProductOption> findByIdAndActiveTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select po
            from ProductOption po
            join fetch po.product
            where po.id in :ids
            order by po.id asc
            """)
    List<ProductOption> findAllByIdInWithPessimisticLock(@Param("ids") List<Long> ids);
}
