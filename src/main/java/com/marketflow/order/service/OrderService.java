package com.marketflow.order.service;

import com.marketflow.cart.domain.CartItem;
import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderItem;
import com.marketflow.order.dto.OrderCreateRequest;
import com.marketflow.order.dto.OrderCreateResponse;
import com.marketflow.order.dto.OrderDetailResponse;
import com.marketflow.order.dto.OrderSummaryResponse;
import com.marketflow.order.repository.OrderItemRepository;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.exception.InactiveProductOptionException;
import com.marketflow.product.exception.InsufficientStockException;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int ORDER_NO_RANDOM_LENGTH = 8;
    private static final int ORDER_NO_MAX_RETRY_COUNT = 5;
    private static final int ORDER_CREATE_MAX_RETRY_COUNT = 10;
    private static final long ORDER_CREATE_RETRY_BACKOFF_MIN_MILLIS = 10L;
    private static final long ORDER_CREATE_RETRY_BACKOFF_MAX_MILLIS = 50L;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductOptionRepository productOptionRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrders(Long userId, Pageable pageable) {
        User user = getUser(userId);

        return orderRepository.findByUserId(user.getId(), pageable)
                .map(OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrder(Long userId, Long orderId) {
        Order order = getOrderByUser(userId, orderId);
        List<OrderItem> orderItems = getOrderItems(order.getId());

        return OrderDetailResponse.of(order, orderItems);
    }

    public OrderCreateResponse createOrder(Long userId, OrderCreateRequest request) {
        OptimisticLockingFailureException lastFailure = null;

        for (int retryCount = 0; retryCount < ORDER_CREATE_MAX_RETRY_COUNT; retryCount++) {
            try {
                return transactionTemplate.execute(status -> createOrderInTransaction(userId, request));
            } catch (OptimisticLockingFailureException exception) {
                lastFailure = exception;
                backoffBeforeRetry(retryCount, exception);
            }
        }

        throw lastFailure;
    }

    private void backoffBeforeRetry(int retryCount, OptimisticLockingFailureException exception) {
        if (retryCount == ORDER_CREATE_MAX_RETRY_COUNT - 1) {
            return;
        }

        try {
            Thread.sleep(calculateRandomBackoffMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private long calculateRandomBackoffMillis() {
        return ThreadLocalRandom.current().nextLong(
                ORDER_CREATE_RETRY_BACKOFF_MIN_MILLIS,
                ORDER_CREATE_RETRY_BACKOFF_MAX_MILLIS + 1
        );
    }

    private OrderCreateResponse createOrderInTransaction(Long userId, OrderCreateRequest request) {
        User user = getUser(userId);
        List<Long> cartItemIds = normalizeCartItemIds(request);
        List<CartItem> cartItems = getCartItems(user.getId(), cartItemIds);

        validateOrderableCartItems(cartItems);

        Long totalAmount = calculateTotalAmount(cartItems);

        decreaseStock(cartItems);
        productOptionRepository.flush();

        Order order = orderRepository.save(new Order(generateOrderNo(), user, totalAmount));
        List<OrderItem> orderItems = cartItems.stream()
                .map(cartItem -> new OrderItem(
                        order,
                        cartItem.getProductOption(),
                        cartItem.getQuantity()
                ))
                .toList();

        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);
        cartItemRepository.deleteAll(cartItems);

        return OrderCreateResponse.of(order, savedOrderItems);
    }

    @Transactional
    public OrderDetailResponse cancelOrder(Long userId, Long orderId) {
        Order order = getOrderByUser(userId, orderId);
        List<OrderItem> orderItems = getOrderItems(order.getId());

        order.cancel();
        orderItems.forEach(orderItem ->
                orderItem.getProductOption().increaseStock(orderItem.getQuantity())
        );

        return OrderDetailResponse.of(order, orderItems);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다. id=" + userId));
    }

    private Order getOrderByUser(Long userId, Long orderId) {
        return orderRepository.findByUserIdAndId(userId, orderId)
                .orElseThrow(() -> new NoSuchElementException("주문을 찾을 수 없습니다. id=" + orderId));
    }

    private List<OrderItem> getOrderItems(Long orderId) {
        return orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
    }

    private List<Long> normalizeCartItemIds(OrderCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("주문할 장바구니 항목을 선택해야 합니다.");
        }

        List<Long> cartItemIds = request.cartItemIds();
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new IllegalArgumentException("주문할 장바구니 항목을 선택해야 합니다.");
        }

        Set<Long> uniqueCartItemIds = new LinkedHashSet<>();
        for (Long cartItemId : cartItemIds) {
            if (cartItemId == null) {
                throw new IllegalArgumentException("장바구니 항목 ID는 null일 수 없습니다.");
            }
            uniqueCartItemIds.add(cartItemId);
        }

        return List.copyOf(uniqueCartItemIds);
    }

    private List<CartItem> getCartItems(Long userId, List<Long> cartItemIds) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdAndIdInOrderByIdAsc(userId, cartItemIds);

        if (cartItems.size() != cartItemIds.size()) {
            throw new NoSuchElementException("주문할 장바구니 항목을 찾을 수 없습니다.");
        }

        return cartItems;
    }

    private void validateOrderableCartItems(List<CartItem> cartItems) {
        for (CartItem cartItem : cartItems) {
            ProductOption productOption = cartItem.getProductOption();

            if (!productOption.isActive()) {
                throw new InactiveProductOptionException();
            }

            if (productOption.getStockQuantity() < cartItem.getQuantity()) {
                throw new InsufficientStockException();
            }
        }
    }

    private void decreaseStock(List<CartItem> cartItems) {
        cartItems.stream()
                .sorted(Comparator.comparing(cartItem -> cartItem.getProductOption().getId()))
                .forEach(cartItem -> cartItem.getProductOption().decreaseStock(cartItem.getQuantity()));
    }

    private Long calculateTotalAmount(List<CartItem> cartItems) {
        return cartItems.stream()
                .mapToLong(cartItem -> {
                    ProductOption productOption = cartItem.getProductOption();
                    return productOption.getPrice() * cartItem.getQuantity();
                })
                .sum();
    }

    private String generateOrderNo() {
        for (int retryCount = 0; retryCount < ORDER_NO_MAX_RETRY_COUNT; retryCount++) {
            String orderNo = LocalDate.now().format(ORDER_NO_DATE_FORMATTER)
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, ORDER_NO_RANDOM_LENGTH)
                    .toUpperCase();

            if (!orderRepository.existsByOrderNo(orderNo)) {
                return orderNo;
            }
        }

        throw new IllegalStateException("주문 번호를 생성할 수 없습니다.");
    }
}
