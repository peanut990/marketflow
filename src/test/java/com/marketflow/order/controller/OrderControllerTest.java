package com.marketflow.order.controller;

import com.marketflow.cart.domain.CartItem;
import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderItem;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private User user;
    private User otherUser;
    private Product product;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        userRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();

        user = userRepository.save(new User(
                "order-test@marketflow.com",
                "password",
                "주문 테스트 사용자",
                "010-2222-2222"
        ));
        otherUser = userRepository.save(new User(
                "other-order-test@marketflow.com",
                "password",
                "다른 주문 테스트 사용자",
                "010-3333-3333"
        ));
        product = productRepository.save(new Product(
                "Order Test Product",
                "주문 테스트 상품입니다.",
                "TEST",
                "https://example.com/images/order-test-product.jpg"
        ));
    }

    @Test
    void getOrders() throws Exception {
        ProductOption firstOption = productOptionRepository.save(new ProductOption(product, "First Option", 1000L, 10));
        ProductOption secondOption = productOptionRepository.save(new ProductOption(product, "Second Option", 2500L, 10));
        Order createdOrder = createOrderWithItem(user, firstOption, 2);
        Order canceledOrder = createOrderWithItem(user, secondOption, 1);
        canceledOrder.cancel();
        orderRepository.save(canceledOrder);
        createOrderWithItem(otherUser, firstOption, 1);

        mockMvc.perform(get("/api/orders")
                        .header("X-USER-ID", user.getId())
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].orderId").value(createdOrder.getId()))
                .andExpect(jsonPath("$.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.content[1].orderId").value(canceledOrder.getId()))
                .andExpect(jsonPath("$.content[1].status").value("CANCELED"));
    }

    @Test
    void getOrder() throws Exception {
        ProductOption productOption = productOptionRepository.save(new ProductOption(product, "Detail Option", 1500L, 10));
        Order order = createOrderWithItem(user, productOption, 3);

        mockMvc.perform(get("/api/orders/{orderId}", order.getId())
                        .header("X-USER-ID", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.orderNo").value(order.getOrderNo()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(4500L))
                .andExpect(jsonPath("$.orderedAt").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productOptionId").value(productOption.getId()))
                .andExpect(jsonPath("$.items[0].productName").value(product.getName()))
                .andExpect(jsonPath("$.items[0].optionName").value(productOption.getName()))
                .andExpect(jsonPath("$.items[0].orderPrice").value(productOption.getPrice()))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].totalPrice").value(4500L));
    }

    @Test
    void createOrder() throws Exception {
        ProductOption firstOption = productOptionRepository.save(new ProductOption(product, "First Option", 1000L, 10));
        ProductOption secondOption = productOptionRepository.save(new ProductOption(product, "Second Option", 2500L, 8));
        CartItem firstCartItem = cartItemRepository.save(new CartItem(user, firstOption, 2));
        CartItem secondCartItem = cartItemRepository.save(new CartItem(user, secondOption, 3));
        Long expectedTotalAmount = 1000L * 2 + 2500L * 3;

        mockMvc.perform(post("/api/orders")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(firstCartItem.getId(), secondCartItem.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.orderNo").isString())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(expectedTotalAmount))
                .andExpect(jsonPath("$.orderedAt").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2));

        List<Order> orders = orderRepository.findAll();
        List<OrderItem> orderItems = orderItemRepository.findAll();
        ProductOption updatedFirstOption = productOptionRepository.findById(firstOption.getId()).orElseThrow();
        ProductOption updatedSecondOption = productOptionRepository.findById(secondOption.getId()).orElseThrow();

        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().getTotalAmount()).isEqualTo(expectedTotalAmount);
        assertThat(orderItems).hasSize(2);
        assertThat(orderItems)
                .extracting(OrderItem::getTotalPrice)
                .containsExactlyInAnyOrder(2000L, 7500L);
        assertThat(updatedFirstOption.getStockQuantity()).isEqualTo(8);
        assertThat(updatedSecondOption.getStockQuantity()).isEqualTo(5);
        assertThat(cartItemRepository.findById(firstCartItem.getId())).isEmpty();
        assertThat(cartItemRepository.findById(secondCartItem.getId())).isEmpty();
    }

    @Test
    void createOrderRollsBackWhenSecondProductOptionIsOutOfStock() throws Exception {
        ProductOption firstOption = productOptionRepository.save(new ProductOption(product, "Enough Stock Option", 1000L, 10));
        ProductOption secondOption = productOptionRepository.save(new ProductOption(product, "Out Of Stock Option", 2500L, 1));
        CartItem firstCartItem = cartItemRepository.save(new CartItem(user, firstOption, 2));
        CartItem secondCartItem = cartItemRepository.save(new CartItem(user, secondOption, 2));

        mockMvc.perform(post("/api/orders")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(firstCartItem.getId(), secondCartItem.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        ProductOption updatedFirstOption = productOptionRepository.findById(firstOption.getId()).orElseThrow();
        ProductOption updatedSecondOption = productOptionRepository.findById(secondOption.getId()).orElseThrow();

        assertThat(updatedFirstOption.getStockQuantity()).isEqualTo(10);
        assertThat(updatedSecondOption.getStockQuantity()).isEqualTo(1);
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(orderItemRepository.findAll()).isEmpty();
        assertThat(cartItemRepository.findById(firstCartItem.getId())).isPresent();
        assertThat(cartItemRepository.findById(secondCartItem.getId())).isPresent();
    }

    @Test
    void createOrderFailsWhenInactiveProductOptionIsIncluded() throws Exception {
        ProductOption firstOption = productOptionRepository.save(new ProductOption(product, "Active Option", 1000L, 10));
        ProductOption secondOption = productOptionRepository.save(new ProductOption(product, "Inactive Option", 2500L, 8));
        secondOption.deactivate();
        productOptionRepository.save(secondOption);
        CartItem firstCartItem = cartItemRepository.save(new CartItem(user, firstOption, 2));
        CartItem secondCartItem = cartItemRepository.save(new CartItem(user, secondOption, 2));

        mockMvc.perform(post("/api/orders")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d, %d]
                                }
                                """.formatted(firstCartItem.getId(), secondCartItem.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        ProductOption updatedFirstOption = productOptionRepository.findById(firstOption.getId()).orElseThrow();
        ProductOption updatedSecondOption = productOptionRepository.findById(secondOption.getId()).orElseThrow();

        assertThat(updatedFirstOption.getStockQuantity()).isEqualTo(10);
        assertThat(updatedSecondOption.getStockQuantity()).isEqualTo(8);
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(orderItemRepository.findAll()).isEmpty();
        assertThat(cartItemRepository.findById(firstCartItem.getId())).isPresent();
        assertThat(cartItemRepository.findById(secondCartItem.getId())).isPresent();
    }

    @Test
    void createOrderWithEmptyCartItemIds() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void createOrderWithUnknownCartItem() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [%d]
                                }
                                """.formatted(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getOrderNotFoundWhenOrderDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}", Long.MAX_VALUE)
                        .header("X-USER-ID", user.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getOrderNotFoundWhenOrderBelongsToAnotherUser() throws Exception {
        ProductOption productOption = productOptionRepository.save(new ProductOption(product, "Other User Option", 1000L, 10));
        Order order = createOrderWithItem(otherUser, productOption, 1);

        mockMvc.perform(get("/api/orders/{orderId}", order.getId())
                        .header("X-USER-ID", user.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void cancelOrder() throws Exception {
        ProductOption productOption = productOptionRepository.save(new ProductOption(product, "Cancel Option", 1000L, 10));
        Order order = createOrderWithItem(user, productOption, 3);
        productOption.decreaseStock(3);
        productOptionRepository.save(productOption);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
                        .header("X-USER-ID", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        ProductOption updatedProductOption = productOptionRepository.findById(productOption.getId()).orElseThrow();

        assertThat(updatedOrder.getStatus().name()).isEqualTo("CANCELED");
        assertThat(updatedProductOption.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void cancelOrderFailsWhenAlreadyCanceled() throws Exception {
        ProductOption productOption = productOptionRepository.save(new ProductOption(product, "Already Canceled Option", 1000L, 10));
        Order order = createOrderWithItem(user, productOption, 1);
        order.cancel();
        orderRepository.save(order);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
                        .header("X-USER-ID", user.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void cancelOrderNotFoundWhenOrderBelongsToAnotherUser() throws Exception {
        ProductOption productOption = productOptionRepository.save(new ProductOption(product, "Other User Cancel Option", 1000L, 10));
        Order order = createOrderWithItem(otherUser, productOption, 1);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
                        .header("X-USER-ID", user.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private Order createOrderWithItem(User orderUser, ProductOption productOption, int quantity) {
        Order order = orderRepository.save(new Order(
                "TEST-" + orderUser.getId() + "-" + productOption.getId() + "-" + quantity + "-" + System.nanoTime(),
                orderUser,
                productOption.getPrice() * quantity
        ));
        orderItemRepository.save(new OrderItem(order, productOption, quantity));

        return order;
    }
}
