package com.marketflow.order.service;

import com.marketflow.cart.domain.CartItem;
import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.order.dto.OrderCreateRequest;
import com.marketflow.order.repository.OrderItemRepository;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.product.repository.ProductRepository;
import com.marketflow.support.IntegrationTest;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceConcurrencyTest extends IntegrationTest {

    private static final int USER_COUNT = 10;
    private static final int INITIAL_STOCK_QUANTITY = 10;
    private static final int ORDER_QUANTITY = 1;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

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
    void createOrderConcurrentlyWithSameStockShouldCreateAllOrdersAndReduceStockToZero() throws Exception {
        Product product = productRepository.save(new Product(
                "Concurrency Test Product",
                "동시성 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/concurrency-test-product.jpg"
        ));
        ProductOption productOption = productOptionRepository.save(new ProductOption(
                product,
                "Concurrency Test Option",
                1000L,
                INITIAL_STOCK_QUANTITY
        ));
        List<OrderAttempt> orderAttempts = createOrderAttempts(productOption);

        ExecutorService executorService = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(USER_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(USER_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        try {
            for (OrderAttempt orderAttempt : orderAttempts) {
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        orderService.createOrder(
                                orderAttempt.userId(),
                                new OrderCreateRequest(List.of(orderAttempt.cartItemId()))
                        );
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
//        List<Throwable> failures = new CopyOnWriteArrayList<>();
//
//        AtomicInteger successCount = new AtomicInteger();
//        for (OrderAttempt orderAttempt : orderAttempts) {
//            executorService.submit(() -> {
//
//                try {
//
//                    orderService.createOrder(
//
//                            orderAttempt.userId(),
//
//                            new OrderCreateRequest(List.of(orderAttempt.cartItemId()))
//
//                    );
//
//                    successCount.incrementAndGet();
//
//                } catch (Throwable throwable) {
//
//                    failures.add(throwable);
//
//                }
//
//            });
//        }
//
//
//        executorService.shutdown();
//
//        boolean completed = executorService.awaitTermination(30, TimeUnit.SECONDS);
//
        ProductOption updatedProductOption = productOptionRepository.findById(productOption.getId()).orElseThrow();

        System.out.println(updatedProductOption.getStockQuantity() + "####################");
        System.out.println("successCount = " + successCount.get());
        System.out.println("failureCount = " + failures.size());

        failures.forEach(Throwable::printStackTrace);
        assertThat(successCount.get()).isEqualTo(USER_COUNT);
        assertThat(failures).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(USER_COUNT);
        assertThat(orderItemRepository.count()).isEqualTo(USER_COUNT);
        assertThat(cartItemRepository.count()).isZero();
        assertThat(updatedProductOption.getStockQuantity()).isZero();
    }

    private List<OrderAttempt> createOrderAttempts(ProductOption productOption) {
        List<OrderAttempt> orderAttempts = new ArrayList<>();

        for (int index = 0; index < USER_COUNT; index++) {
            User user = userRepository.save(new User(
                    "concurrency-order-test-" + index + "@marketflow.com",
                    "password",
                    "동시성 주문 테스트 사용자 " + index,
                    "010-4000-%04d".formatted(index)
            ));
            CartItem cartItem = cartItemRepository.save(new CartItem(user, productOption, ORDER_QUANTITY));
            orderAttempts.add(new OrderAttempt(user.getId(), cartItem.getId()));
        }

        return orderAttempts;
    }

    private record OrderAttempt(Long userId, Long cartItemId) {
    }
}
