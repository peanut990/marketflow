package com.marketflow.product.domain;

import com.marketflow.product.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductOptionTest {

    @Test
    void 재고_차감_시_재고가_부족하면_예외가_발생한다() {
        Product product = new Product(
                "Stock Test Product",
                "재고 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/stock-test-product.jpg"
        );
        ProductOption productOption = new ProductOption(product, "Stock Test Option", 1000L, 1);

        assertThatThrownBy(() -> productOption.decreaseStock(2))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("재고가 부족합니다.");
    }
}
