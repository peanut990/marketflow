package com.marketflow.payment.service;

import com.marketflow.cart.domain.CartItem;
import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderItem;
import com.marketflow.order.domain.OrderStatus;
import com.marketflow.order.dto.OrderCreateRequest;
import com.marketflow.order.repository.OrderItemRepository;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.order.service.OrderService;
import com.marketflow.payment.domain.Payment;
import com.marketflow.payment.domain.PaymentStatus;
import com.marketflow.payment.exception.PaymentConflictException;
import com.marketflow.payment.repository.PaymentRepository;
import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.product.repository.ProductRepository;
import com.marketflow.support.IntegrationTest;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentConcurrencyTest extends IntegrationTest {

    @Autowired PaymentService paymentService;
    @Autowired OrderService orderService;
    @Autowired PaymentRepository payments;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired ProductOptionRepository options;
    @Autowired CartItemRepository carts;
    @Autowired PlatformTransactionManager transactionManager;

    private User user;
    private ProductOption option;
    private Order order;

    @BeforeEach
    void setUp() {
        user = users.save(new User("payment-concurrency@marketflow.com", "password", "동시성 사용자", "010-1111-1111"));
        Product product = products.save(new Product("Product", "Description", "TEST", "https://example.com/test.jpg"));
        option = options.save(new ProductOption(product, "Option", 1000L, 99));
        order = createReservedOrder();
    }

    @AfterEach
    void cleanUp() {
        payments.deleteAll();
        orderItems.deleteAll();
        orders.deleteAll();
        carts.deleteAll();
        users.deleteAll();
        options.deleteAll();
        products.deleteAll();
    }

    @Test
    void 동일_주문의_20개_동시준비는_결제를_한번만_생성한다() throws Exception {
        List<Callable<PaymentService.Preparation>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) tasks.add(() -> paymentService.prepare(user.getId(), order.getId()));
        List<PaymentService.Preparation> results = runTogether(tasks);
        assertThat(results.stream().filter(PaymentService.Preparation::created).count()).isEqualTo(1);
        assertThat(results.stream().map(r -> r.payment().paymentId()).distinct().count()).isEqualTo(1);
        assertThat(payments.findAllByOrderIdOrderByCreatedAtAsc(order.getId())).hasSize(1);
        assertThat(stock()).isEqualTo(99);
    }

    @Test
    void 준비가_락을_먼저_획득하면_뒤따른_취소가_READY를_만료시킨다() throws Exception {
        runOrderedRace(
                () -> paymentService.prepare(user.getId(), order.getId()),
                () -> orderService.cancelOrder(user.getId(), order.getId()));
        assertThat(payments.findAllByOrderIdOrderByCreatedAtAsc(order.getId()))
                .singleElement().extracting(Payment::getStatus).isEqualTo(PaymentStatus.EXPIRED);
        assertCanceledOnce();
    }

    @Test
    void 취소가_락을_먼저_획득하면_뒤따른_준비는_거절된다() throws Exception {
        runOrderedRace(
                () -> orderService.cancelOrder(user.getId(), order.getId()),
                () -> {
                    assertThatThrownBy(() -> paymentService.prepare(user.getId(), order.getId()))
                            .isInstanceOf(PaymentConflictException.class)
                            .extracting("code").isEqualTo("ORDER_NOT_PAYABLE");
                    return null;
                });
        assertThat(payments.findAllByOrderIdOrderByCreatedAtAsc(order.getId())).isEmpty();
        assertCanceledOnce();
    }

    @Test
    void 동시_중복취소는_재고를_한번만_복구한다() throws Exception {
        paymentService.prepare(user.getId(), order.getId());
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    orderService.cancelOrder(user.getId(), order.getId());
                    return true;
                } catch (IllegalStateException expected) {
                    return false;
                }
            });
        }
        assertThat(runTogether(tasks)).filteredOn(Boolean::booleanValue).hasSize(1);
        assertCanceledOnce();
    }

    @Test
    void 같은_옵션의_주문생성과_여러_주문취소가_겹쳐도_재고갱신이_유실되지_않는다() throws Exception {
        List<Order> reserved = new ArrayList<>();
        reserved.add(order);
        for (int i = 0; i < 9; i++) reserved.add(createReservedOrder());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                options.decreaseStockAtomically(option.getId(), 9));
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (Order existing : reserved) {
            tasks.add(() -> {
                orderService.cancelOrder(user.getId(), existing.getId());
                return true;
            });
        }
        for (int i = 0; i < 10; i++) {
            User buyer = users.save(new User("buyer-" + i + "@marketflow.com", "password", "구매자", "010-2222-%04d".formatted(i)));
            CartItem cart = carts.save(new CartItem(buyer, option, 1));
            tasks.add(() -> {
                orderService.createOrder(buyer.getId(), new OrderCreateRequest(List.of(cart.getId())));
                return true;
            });
        }
        assertThat(runTogether(tasks)).containsOnly(true).hasSize(20);
        assertThat(stock()).isEqualTo(90);
        for (Order existing : reserved) {
            assertThat(orders.findById(existing.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        }
    }

    private Order createReservedOrder() {
        Order reserved = orders.save(new Order(UUID.randomUUID().toString(), user, 1000L));
        orderItems.save(new OrderItem(reserved, option, 1));
        return reserved;
    }

    @Test
    void 두번째_재고복구가_실패하면_첫번째_복구와_주문취소와_결제만료를_롤백한다() {
        Product product = products.save(new Product("Overflow", "Description", "TEST", "https://example.com/test.jpg"));
        ProductOption overflow = options.save(new ProductOption(product, "Overflow", 1000L, Integer.MAX_VALUE));
        orderItems.save(new OrderItem(order, overflow, 1));
        Long paymentId = paymentService.prepare(user.getId(), order.getId()).payment().paymentId();

        assertThatThrownBy(() -> orderService.cancelOrder(user.getId(), order.getId()))
                .isInstanceOf(DataAccessException.class);

        assertThat(stock()).isEqualTo(99);
        assertThat(options.findById(overflow.getId()).orElseThrow().getStockQuantity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.READY);
    }

    private int stock() {
        return options.findById(option.getId()).orElseThrow().getStockQuantity();
    }

    private void assertCanceledOnce() {
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(stock()).isEqualTo(100);
        assertThatThrownBy(() -> orderService.cancelOrder(user.getId(), order.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(stock()).isEqualTo(100);
    }

    private void runOrderedRace(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstResult = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                orders.findByUserIdAndIdForUpdate(user.getId(), order.getId()).orElseThrow();
                Object result = call(first);
                lockHeld.countDown();
                await(secondStarted);
                return result;
            }));
            Future<?> secondResult = executor.submit(() -> {
                await(lockHeld);
                secondStarted.countDown();
                return call(second);
            });
            firstResult.get(30, TimeUnit.SECONDS);
            secondResult.get(30, TimeUnit.SECONDS);
        } finally {
            stop(executor);
        }
    }

    private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) futures.add(executor.submit(() -> {
                ready.countDown();
                await(start);
                return task.call();
            }));
            await(ready);
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            stop(executor);
        }
    }

    private static Object call(Callable<?> task) {
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("작업 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }
}
