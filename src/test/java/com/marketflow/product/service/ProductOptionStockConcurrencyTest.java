package com.marketflow.product.service;

import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.order.repository.OrderItemRepository;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.product.repository.ProductRepository;
import com.marketflow.support.IntegrationTest;
import com.marketflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProductOptionStockConcurrencyTest extends IntegrationTest {

    private static final int REQUEST_COUNT = 20;
    private static final int INITIAL_STOCK_QUANTITY = 10;
    private static final int DECREASE_QUANTITY = 1;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        userRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void atomic_update로_동시_재고_차감을_정합성있게_처리한다() throws Exception {
        Product product = productRepository.save(new Product(
                "Atomic Update Test Product",
                "Atomic UPDATE 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/atomic-update-test-product.jpg"
        ));
        ProductOption productOption = productOptionRepository.save(new ProductOption(
                product,
                "Atomic Update Test Option",
                1000L,
                INITIAL_STOCK_QUANTITY
        ));

        ExecutorService executorService = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(REQUEST_COUNT);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger stockShortageFailureCount = new AtomicInteger();
        CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();

        try {
            for (int index = 0; index < REQUEST_COUNT; index++) {
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        Integer affectedRows = transactionTemplate.execute(status ->
                                productOptionRepository.decreaseStockAtomically(
                                        productOption.getId(),
                                        DECREASE_QUANTITY
                                )
                        );

                        if (affectedRows == 1) {
                            successCount.incrementAndGet();
                        } else {
                            stockShortageFailureCount.incrementAndGet();
                        }
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executorService.shutdownNow();
        }

        ProductOption updatedProductOption = productOptionRepository.findById(productOption.getId()).orElseThrow();

        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK_QUANTITY);
        assertThat(stockShortageFailureCount.get()).isEqualTo(REQUEST_COUNT - INITIAL_STOCK_QUANTITY);
        assertThat(failures).isEmpty();
        assertThat(updatedProductOption.getStockQuantity()).isZero();
    }

}
