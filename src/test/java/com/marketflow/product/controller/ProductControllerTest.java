package com.marketflow.product.controller;

import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.product.repository.ProductRepository;
import com.marketflow.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        product = productRepository.save(new Product(
                "Product API Test Product",
                "상품 API 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/product-api-test-product.jpg"
        ));
        productOptionRepository.save(new ProductOption(product, "Default Option", 1000L, 10));
    }

    @Test
    void 상품_목록을_조회한다() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").exists())
                .andExpect(jsonPath("$.content[0].category").exists());
    }

    @Test
    void 상품_상세를_조회한다() throws Exception {
        mockMvc.perform(get("/api/products/{productId}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value(product.getName()))
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.options[0].id").exists())
                .andExpect(jsonPath("$.options[0].price").exists());
    }

    @Test
    void 존재하지_않는_상품_조회는_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/{productId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }
}
