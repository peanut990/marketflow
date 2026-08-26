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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProductOptionStockConcurrencyTest extends IntegrationTest {

    private static final int THREAD_COUNT = 10;
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

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        userRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void decreaseStockConcurrentlyWithOptimisticLockShouldDetectConflicts() throws Exception {
        Product product = productRepository.save(new Product(
                "Lost Update Test Product",
                "갱신 손실 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/lost-update-test-product.jpg"
        ));
        ProductOption productOption = productOptionRepository.save(new ProductOption(
                product,
                "Lost Update Test Option",
                1000L,
                INITIAL_STOCK_QUANTITY
        ));

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch readLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        try {
            for (int index = 0; index < THREAD_COUNT; index++) {
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        transactionTemplate.executeWithoutResult(status -> {
                            ProductOption foundProductOption = productOptionRepository.findById(productOption.getId())
                                    .orElseThrow();

                            readLatch.countDown();
                            await(readLatch);

                            foundProductOption.decreaseStock(DECREASE_QUANTITY);
                        });
                        successCount.incrementAndGet();
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

        assertThat(successCount.get()).isBetween(1, THREAD_COUNT - 1);
        assertThat(failures)
                .hasSize(THREAD_COUNT - successCount.get())
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(OptimisticLockingFailureException.class));
        assertThat(updatedProductOption.getStockQuantity())
                .isEqualTo(INITIAL_STOCK_QUANTITY - successCount.get() * DECREASE_QUANTITY);
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
