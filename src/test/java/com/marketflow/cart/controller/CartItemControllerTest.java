package com.marketflow.cart.controller;

import com.marketflow.cart.domain.CartItem;
import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CartItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private ProductOption productOption;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        user = userRepository.findByEmail("test@marketflow.com").orElseThrow();
        productOption = productOptionRepository.findAll().getFirst();
    }

    @Test
    void getCartItems() throws Exception {
        cartItemRepository.save(new CartItem(user, productOption, 2));

        mockMvc.perform(get("/api/cart-items")
                        .header("X-USER-ID", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].cartItemId").exists())
                .andExpect(jsonPath("$[0].productOptionId").value(productOption.getId()))
                .andExpect(jsonPath("$[0].productName").exists())
                .andExpect(jsonPath("$[0].optionName").value(productOption.getName()))
                .andExpect(jsonPath("$[0].price").value(productOption.getPrice()))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].totalPrice").value(productOption.getPrice() * 2));
    }

    @Test
    void addCartItem() throws Exception {
        mockMvc.perform(post("/api/cart-items")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productOptionId": %d,
                                  "quantity": 3
                                }
                                """.formatted(productOption.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productOptionId").value(productOption.getId()))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.totalPrice").value(productOption.getPrice() * 3));

        List<CartItem> cartItems = cartItemRepository.findAll();
        assertThat(cartItems).hasSize(1);
        assertThat(cartItems.getFirst().getQuantity()).isEqualTo(3);
    }

    @Test
    void addCartItemIncreasesQuantityWhenSameProductOptionExists() throws Exception {
        cartItemRepository.save(new CartItem(user, productOption, 2));

        mockMvc.perform(post("/api/cart-items")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productOptionId": %d,
                                  "quantity": 3
                                }
                                """.formatted(productOption.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productOptionId").value(productOption.getId()))
                .andExpect(jsonPath("$.quantity").value(5));

        List<CartItem> cartItems = cartItemRepository.findAll();
        assertThat(cartItems).hasSize(1);
        assertThat(cartItems.getFirst().getQuantity()).isEqualTo(5);
    }

    @Test
    void updateCartItem() throws Exception {
        CartItem cartItem = cartItemRepository.save(new CartItem(user, productOption, 2));

        mockMvc.perform(patch("/api/cart-items/{cartItemId}", cartItem.getId())
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItemId").value(cartItem.getId()))
                .andExpect(jsonPath("$.quantity").value(7));
    }

    @Test
    void deleteCartItem() throws Exception {
        CartItem cartItem = cartItemRepository.save(new CartItem(user, productOption, 2));

        mockMvc.perform(delete("/api/cart-items/{cartItemId}", cartItem.getId())
                        .header("X-USER-ID", user.getId()))
                .andExpect(status().isNoContent());

        assertThat(cartItemRepository.findById(cartItem.getId())).isEmpty();
    }

    @Test
    void addCartItemWithInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/cart-items")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productOptionId": %d,
                                  "quantity": 0
                                }
                                """.formatted(productOption.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void getCartItemsWithUnknownUser() throws Exception {
        mockMvc.perform(get("/api/cart-items")
                        .header("X-USER-ID", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void addCartItemWithUnknownProductOption() throws Exception {
        mockMvc.perform(post("/api/cart-items")
                        .header("X-USER-ID", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productOptionId": %d,
                                  "quantity": 1
                                }
                                """.formatted(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
