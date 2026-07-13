package com.marketflow.product.controller;

import com.marketflow.product.dto.ProductDetailResponse;
import com.marketflow.product.dto.ProductSummaryResponse;
import com.marketflow.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Page<ProductSummaryResponse> getProducts(
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = DESC)
            Pageable pageable
    ) {
        return productService.getProducts(pageable);
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse getProduct(@PathVariable Long productId) {
        return productService.getProduct(productId);
    }
}
