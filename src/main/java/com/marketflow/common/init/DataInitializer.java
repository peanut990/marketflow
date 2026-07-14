package com.marketflow.common.init;

import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.product.repository.ProductRepository;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser();
        seedProducts();
    }

    private void seedUser() {
        userRepository.findByEmail("test@marketflow.com")
                .orElseGet(() -> userRepository.save(new User(
                        "test@marketflow.com",
                        "password",
                        "테스트 사용자",
                        "010-0000-0000"
                )));
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            return;
        }

        Product hoodie = new Product(
                "Marketflow Hoodie",
                "부드러운 기모 안감의 데일리 후디입니다.",
                "APPAREL",
                "https://example.com/images/marketflow-hoodie.jpg"
        );
        Product tumbler = new Product(
                "Marketflow Tumbler",
                "출근길과 캠핑에 모두 어울리는 스테인리스 텀블러입니다.",
                "LIFESTYLE",
                "https://example.com/images/marketflow-tumbler.jpg"
        );
        Product keyboard = new Product(
                "Marketflow Keyboard",
                "조용한 타건감의 무선 기계식 키보드입니다.",
                "DIGITAL",
                "https://example.com/images/marketflow-keyboard.jpg"
        );

        productRepository.saveAll(List.of(hoodie, tumbler, keyboard));

        productOptionRepository.saveAll(List.of(
                new ProductOption(hoodie, "Black / M", 59000L, 40),
                new ProductOption(hoodie, "Gray / L", 59000L, 35),
                new ProductOption(tumbler, "Matte Black / 500ml", 24000L, 80),
                new ProductOption(tumbler, "Cream White / 500ml", 24000L, 75),
                new ProductOption(keyboard, "White / Linear", 129000L, 20),
                new ProductOption(keyboard, "Black / Tactile", 139000L, 18)
        ));
    }
}
