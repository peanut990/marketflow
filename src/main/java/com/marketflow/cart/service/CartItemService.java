package com.marketflow.cart.service;

import com.marketflow.cart.domain.CartItem;
import com.marketflow.cart.dto.CartItemCreateRequest;
import com.marketflow.cart.dto.CartItemResponse;
import com.marketflow.cart.dto.CartItemUpdateRequest;
import com.marketflow.cart.repository.CartItemRepository;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final ProductOptionRepository productOptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CartItemResponse> getCartItems(Long userId) {
        validateUserExists(userId);

        return cartItemRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(CartItemResponse::from)
                .toList();
    }

    @Transactional
    public CartItemResponse addCartItem(Long userId, CartItemCreateRequest request) {
        validatePositiveQuantity(request.quantity());
        User user = getUser(userId);
        ProductOption productOption = getProductOption(request.productOptionId());

        CartItem cartItem = cartItemRepository.findByUserIdAndProductOptionId(user.getId(), productOption.getId())
                .map(existingCartItem -> {
                    existingCartItem.addQuantity(request.quantity());
                    return existingCartItem;
                })
                .orElseGet(() -> cartItemRepository.save(new CartItem(user, productOption, request.quantity())));

        return CartItemResponse.from(cartItem);
    }

    @Transactional
    public CartItemResponse updateCartItem(Long userId, Long cartItemId, CartItemUpdateRequest request) {
        validatePositiveQuantity(request.quantity());
        validateUserExists(userId);

        CartItem cartItem = getCartItem(userId, cartItemId);
        cartItem.changeQuantity(request.quantity());

        return CartItemResponse.from(cartItem);
    }

    @Transactional
    public void deleteCartItem(Long userId, Long cartItemId) {
        validateUserExists(userId);

        CartItem cartItem = getCartItem(userId, cartItemId);
        cartItemRepository.delete(cartItem);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다. id=" + userId));
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("사용자를 찾을 수 없습니다. id=" + userId);
        }
    }

    private ProductOption getProductOption(Long productOptionId) {
        return productOptionRepository.findByIdAndActiveTrue(productOptionId)
                .orElseThrow(() -> new NoSuchElementException("상품 옵션을 찾을 수 없습니다. id=" + productOptionId));
    }

    private CartItem getCartItem(Long userId, Long cartItemId) {
        return cartItemRepository.findByUserIdAndId(userId, cartItemId)
                .orElseThrow(() -> new NoSuchElementException("장바구니 항목을 찾을 수 없습니다. id=" + cartItemId));
    }

    private void validatePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
    }
}
