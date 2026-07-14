package com.marketflow.cart.controller;

import com.marketflow.cart.dto.CartItemCreateRequest;
import com.marketflow.cart.dto.CartItemResponse;
import com.marketflow.cart.dto.CartItemUpdateRequest;
import com.marketflow.cart.service.CartItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart-items")
public class CartItemController {

    private final CartItemService cartItemService;

    @GetMapping
    public List<CartItemResponse> getCartItems(@RequestHeader("X-USER-ID") Long userId) {
        return cartItemService.getCartItems(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse addCartItem(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody CartItemCreateRequest request
    ) {
        return cartItemService.addCartItem(userId, request);
    }

    @PatchMapping("/{cartItemId}")
    public CartItemResponse updateCartItem(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable Long cartItemId,
            @RequestBody CartItemUpdateRequest request
    ) {
        return cartItemService.updateCartItem(userId, cartItemId, request);
    }

    @DeleteMapping("/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCartItem(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable Long cartItemId
    ) {
        cartItemService.deleteCartItem(userId, cartItemId);
    }
}
