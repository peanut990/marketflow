package com.marketflow.order.service;

import com.marketflow.cart.domain.CartItem;
import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.order.dto.OrderCreateRequest;
import com.marketflow.order.repository.OrderItemRepository;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.exception.InactiveProductOptionException;
import com.marketflow.product.exception.InsufficientStockException;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.product.repository.ProductRepository;
import com.marketflow.support.IntegrationTest;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void 재고와_같은_수의_동시_주문은_모두_성공하고_재고가_0이_된다() throws Exception {
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
        ProductOption updatedProductOption = productOptionRepository.findById(productOption.getId()).orElseThrow();

        assertThat(successCount.get()).isEqualTo(USER_COUNT);
        assertThat(failures).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(USER_COUNT);
        assertThat(orderItemRepository.count()).isEqualTo(USER_COUNT);
        assertThat(cartItemRepository.count()).isZero();
        assertThat(updatedProductOption.getStockQuantity()).isZero();
    }

    @Test
    void 재고가_1개일때_동시_주문은_1건만_성공하고_재고가_음수가_되지_않는다() throws Exception {
        Product product = productRepository.save(new Product(
                "Depleted Stock Test Product",
                "재고 소진 동시성 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/depleted-stock-test-product.jpg"
        ));
        ProductOption productOption = productOptionRepository.save(new ProductOption(
                product,
                "Depleted Stock Test Option",
                1000L,
                1
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
        ProductOption updatedProductOption = productOptionRepository.findById(productOption.getId()).orElseThrow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failures).hasSize(USER_COUNT - 1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(cartItemRepository.count()).isEqualTo(USER_COUNT - 1);
        assertThat(updatedProductOption.getStockQuantity()).isZero();
    }

    @Test
    void 재고가_부족하면_주문_생성은_재고_부족_예외로_실패한다() {
        Product product = productRepository.save(new Product(
                "Insufficient Stock Test Product",
                "재고 부족 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/insufficient-stock-test-product.jpg"
        ));
        ProductOption productOption = productOptionRepository.save(new ProductOption(
                product,
                "Insufficient Stock Test Option",
                1000L,
                1
        ));
        User user = userRepository.save(new User(
                "insufficient-stock-order-test@marketflow.com",
                "password",
                "재고 부족 주문 테스트 사용자",
                "010-5000-0001"
        ));
        CartItem cartItem = cartItemRepository.save(new CartItem(user, productOption, 2));

        assertThatThrownBy(() -> orderService.createOrder(user.getId(), new OrderCreateRequest(List.of(cartItem.getId()))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("재고가 부족합니다.");
    }

    @Test
    void 비활성_상품_옵션이면_주문_생성은_비활성_옵션_예외로_실패한다() {
        Product product = productRepository.save(new Product(
                "Inactive Option Test Product",
                "비활성 옵션 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/inactive-option-test-product.jpg"
        ));
        ProductOption productOption = productOptionRepository.save(new ProductOption(
                product,
                "Inactive Option Test Option",
                1000L,
                10
        ));
        productOption.deactivate();
        productOptionRepository.save(productOption);
        User user = userRepository.save(new User(
                "inactive-option-order-test@marketflow.com",
                "password",
                "비활성 옵션 주문 테스트 사용자",
                "010-5000-0002"
        ));
        CartItem cartItem = cartItemRepository.save(new CartItem(user, productOption, 1));

        assertThatThrownBy(() -> orderService.createOrder(user.getId(), new OrderCreateRequest(List.of(cartItem.getId()))))
                .isInstanceOf(InactiveProductOptionException.class)
                .hasMessage("비활성 상품 옵션은 주문할 수 없습니다.");
    }

    @Tag("contention")
    @ParameterizedTest(name = "같은 상품 옵션 동시 주문={0}, 재고={1}")
    @CsvSource({
            "5, 5",
            "10, 10",
            "30, 30",
            "50, 50",
            "100, 10"
    })
    void 같은_상품_옵션_경합_상황별_성공과_실패를_관찰한다(int requestCount, int initialStockQuantity) throws Exception {
        Product product = productRepository.save(new Product(
                "Contention Scenario Test Product " + requestCount,
                "경합 상황별 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/contention-scenario-test-product.jpg"
        ));
        ProductOption productOption = productOptionRepository.save(new ProductOption(
                product,
                "Contention Scenario Test Option",
                1000L,
                initialStockQuantity
        ));
        List<OrderAttempt> orderAttempts = createOrderAttempts(productOption, requestCount);

        OrderExecutionResult result = executeConcurrentOrders(orderAttempts, requestCount);
        ProductOption updatedProductOption = productOptionRepository.findById(productOption.getId()).orElseThrow();

        System.out.printf(
                "[same-option-contention] requests=%d, initialStock=%d, success=%d, stockShortageFailure=%d, retryExhaustedFailure=%d, unexpectedFailure=%d, remainingStock=%d, orders=%d, orderItems=%d, cartItems=%d, elapsedMillis=%d%n",
                requestCount,
                initialStockQuantity,
                result.successCount(),
                result.stockShortageFailureCount(),
                result.retryExhaustedFailureCount(),
                result.unexpectedFailureCount(),
                updatedProductOption.getStockQuantity(),
                orderRepository.count(),
                orderItemRepository.count(),
                cartItemRepository.count(),
                result.elapsedMillis()
        );

        assertThat(result.totalCount()).isEqualTo(requestCount);
        assertThat(result.unexpectedFailureCount()).isZero();
        assertThat(result.retryExhaustedFailureCount()).isZero();
        assertThat(result.successCount()).isBetween(0, initialStockQuantity);
        assertThat(orderRepository.count()).isEqualTo(result.successCount());
        assertThat(orderItemRepository.count()).isEqualTo(result.successCount());
        assertThat(cartItemRepository.count()).isEqualTo(requestCount - result.successCount());
        assertThat(updatedProductOption.getStockQuantity()).isEqualTo(initialStockQuantity - result.successCount());
        assertThat(updatedProductOption.getStockQuantity()).isNotNegative();
    }

    private List<OrderAttempt> createOrderAttempts(ProductOption productOption) {
        return createOrderAttempts(productOption, USER_COUNT);
    }

    private List<OrderAttempt> createOrderAttempts(ProductOption productOption, int userCount) {
        List<OrderAttempt> orderAttempts = new ArrayList<>();

        for (int index = 0; index < userCount; index++) {
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

    private OrderExecutionResult executeConcurrentOrders(List<OrderAttempt> orderAttempts, int threadCount) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger stockShortageFailureCount = new AtomicInteger();
        List<Throwable> unexpectedFailures = new CopyOnWriteArrayList<>();
        long startedAtMillis = System.currentTimeMillis();

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
                    } catch (InsufficientStockException exception) {
                        stockShortageFailureCount.incrementAndGet();
                    } catch (Throwable throwable) {
                        unexpectedFailures.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executorService.shutdownNow();
        }

        return new OrderExecutionResult(
                successCount.get(),
                stockShortageFailureCount.get(),
                0,
                unexpectedFailures.size(),
                System.currentTimeMillis() - startedAtMillis
        );
    }

    private record OrderAttempt(Long userId, Long cartItemId) {
    }

    private record OrderExecutionResult(
            int successCount,
            int stockShortageFailureCount,
            int retryExhaustedFailureCount,
            int unexpectedFailureCount,
            long elapsedMillis
    ) {

        private int totalCount() {
            return successCount + stockShortageFailureCount + retryExhaustedFailureCount + unexpectedFailureCount;
        }
    }
}
