package com.marketflow.product.service;

import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.dto.ProductDetailResponse;
import com.marketflow.product.dto.ProductSummaryResponse;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    public Page<ProductSummaryResponse> getProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable)
                .map(ProductSummaryResponse::from);
    }

    public ProductDetailResponse getProduct(Long productId) {
        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new NoSuchElementException("상품을 찾을 수 없습니다. id=" + productId));
        List<ProductOption> productOptions = productOptionRepository.findByProductIdAndActiveTrueOrderByIdAsc(productId);

        return ProductDetailResponse.of(product, productOptions);
    }
}
