package com.marketflow.payment.controller;

import com.marketflow.order.domain.Order;
import com.marketflow.order.domain.OrderItem;
import com.marketflow.order.domain.OrderStatus;
import com.marketflow.order.repository.OrderItemRepository;
import com.marketflow.order.repository.OrderRepository;
import com.marketflow.payment.domain.Payment;
import com.marketflow.payment.domain.PaymentStatus;
import com.marketflow.payment.repository.PaymentRepository;
import com.marketflow.product.domain.Product;
import com.marketflow.product.domain.ProductOption;
import com.marketflow.product.repository.ProductOptionRepository;
import com.marketflow.product.repository.ProductRepository;
import com.marketflow.support.IntegrationTest;
import com.marketflow.user.domain.User;
import com.marketflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class PaymentControllerTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired PaymentRepository payments;
    @Autowired ProductRepository products;
    @Autowired ProductOptionRepository options;
    @Autowired EntityManager entityManager;

    private User user;
    private Order order;
    private ProductOption option;

    @BeforeEach
    void setUp() {
        user = users.save(new User("payment-api@marketflow.com", "password", "결제 사용자", "010-1111-1111"));
        Product product = products.save(new Product("Product", "Description", "TEST", "https://example.com/test.jpg"));
        option = options.save(new ProductOption(product, "Option", 2000L, 7));
        order = orders.save(new Order(UUID.randomUUID().toString(), user, 3000L));
        orderItems.save(new OrderItem(order, option, 3));
    }

    @Test
    void 서버의_주문금액으로_준비하고_READY는_재사용하며_재고는_변하지_않는다() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/payments", order.getId()).header("X-USER-ID", user.getId()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(3000))
                .andExpect(jsonPath("$.status").value("READY"));
        Payment payment = payments.findAllByOrderIdOrderByCreatedAtAsc(order.getId()).getFirst();
        assertThat(UUID.fromString(payment.getPgOrderId()).toString()).isEqualTo(payment.getPgOrderId());
        mockMvc.perform(post("/api/orders/{id}/payments", order.getId()).header("X-USER-ID", user.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.statusUrl").value("/api/payments/" + payment.getId()))
                .andExpect(header().string("Location", "/api/payments/" + payment.getId()));
        entityManager.flush();
        entityManager.clear();
        assertThat(payments.findAllByOrderIdOrderByCreatedAtAsc(order.getId())).hasSize(1);
        assertThat(options.findById(option.getId()).orElseThrow().getStockQuantity()).isEqualTo(7);
    }

    @Test
    void 승인_진행중이면_같은_결제를_202로_반환한다() throws Exception {
        Payment payment = savePayment(PaymentStatus.IN_PROGRESS);
        mockMvc.perform(post("/api/orders/{id}/payments", order.getId()).header("X-USER-ID", user.getId()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(header().string("Location", "/api/payments/" + payment.getId()));
        assertThat(payments.findAllByOrderIdOrderByCreatedAtAsc(order.getId())).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "EXPIRED"})
    void 확정_실패나_만료_이후에는_새_결제를_준비한다(PaymentStatus state) throws Exception {
        Payment previous = savePayment(state);
        mockMvc.perform(post("/api/orders/{id}/payments", order.getId()).header("X-USER-ID", user.getId()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("READY"));
        assertThat(payments.findAllByOrderIdOrderByCreatedAtAsc(order.getId())).hasSize(2)
                .extracting(Payment::getPgOrderId).doesNotHaveDuplicates();
        assertThat(previous.getStatus()).isEqualTo(state);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"DONE", "CANCELED"})
    void 승인이나_취소된_결제_이력이_있으면_새_결제를_거절한다(PaymentStatus state) throws Exception {
        savePayment(state);
        assertPreparationConflict("ORDER_NOT_PAYABLE");
    }

    @Test
    void 취소된_주문은_결제할_수_없다() throws Exception {
        order.cancel();
        assertPreparationConflict("ORDER_NOT_PAYABLE");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 양수가_아닌_주문금액은_거절한다(long amount) throws Exception {
        order = orders.save(new Order(UUID.randomUUID().toString(), user, amount));
        assertPreparationConflict("ORDER_NOT_PAYABLE");
    }

    @Test
    void 기존_READY와_주문금액이_다르면_거절한다() throws Exception {
        payments.save(new Payment(order, UUID.randomUUID().toString(), 1L));
        assertPreparationConflict("PAYMENT_STATE_CONFLICT");
    }

    @Test
    void 활성_결제가_여러개이면_임의로_선택하지_않는다() throws Exception {
        savePayment(PaymentStatus.READY);
        savePayment(PaymentStatus.IN_PROGRESS);
        assertPreparationConflict("PAYMENT_STATE_CONFLICT");
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void 모든_결제상태를_키_노출이나_상태변경없이_조회한다(PaymentStatus state) throws Exception {
        Payment payment = savePayment(state);
        mockMvc.perform(get("/api/payments/{id}", payment.getId()).header("X-USER-ID", user.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.pgOrderId").value(payment.getPgOrderId()))
                .andExpect(jsonPath("$.amount").value(3000))
                .andExpect(jsonPath("$.orderStatus").value("CREATED"))
                .andExpect(jsonPath("$.status").value(state.name()))
                .andExpect(jsonPath("$.cancelStatus").value(state == PaymentStatus.CANCELED ? "DONE" : "NONE"))
                .andExpect(jsonPath("$.requestedAt").exists())
                .andExpect(jsonPath("$.approvedAt").value(payment.getApprovedAt() == null ? null : payment.getApprovedAt().toString()))
                .andExpect(jsonPath("$.failureCode").value(state == PaymentStatus.FAILED ? "DECLINED" : null))
                .andExpect(jsonPath("$.failureMessage").value(state == PaymentStatus.FAILED ? "거절" : null))
                .andExpect(jsonPath("$.statusUrl").value("/api/payments/" + payment.getId()))
                .andExpect(jsonPath("$.paymentKey").doesNotExist());
        assertThat(payment.getStatus()).isEqualTo(state);
    }

    @Test
    void 타인_자원과_존재하지_않는_자원은_동일하게_404를_반환한다() throws Exception {
        Payment payment = savePayment(PaymentStatus.READY);
        User other = users.save(new User("other-payment@marketflow.com", "password", "다른 사용자", "010-2222-2222"));
        for (Long id : new Long[]{order.getId(), Long.MAX_VALUE}) {
            mockMvc.perform(post("/api/orders/{id}/payments", id).header("X-USER-ID", other.getId()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        for (Long id : new Long[]{payment.getId(), Long.MAX_VALUE}) {
            mockMvc.perform(get("/api/payments/{id}", id).header("X-USER-ID", other.getId()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void 헤더와_경로_ID의_누락_형식_양수조건을_검증한다() throws Exception {
        for (String path : new String[]{"/api/orders/1/payments", "/api/payments/1"}) {
            boolean prepare = path.contains("orders");
            mockMvc.perform(prepare ? post(path) : get(path))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
            for (String invalid : new String[]{"0", "-1", "abc", "9223372036854775808"}) {
                mockMvc.perform((prepare ? post(path) : get(path)).header("X-USER-ID", invalid))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
                String invalidPath = path.replace("/1", "/" + invalid);
                mockMvc.perform((prepare ? post(invalidPath) : get(invalidPath)).header("X-USER-ID", user.getId()))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
            }
        }
    }

    @Test
    void READY가_있는_주문을_취소하면_만료시키고_비활성옵션도_복구한다() throws Exception {
        Payment payment = savePayment(PaymentStatus.READY);
        Payment failed = savePayment(PaymentStatus.FAILED);
        option.deactivate();
        mockMvc.perform(post("/api/orders/{id}/cancel", order.getId()).header("X-USER-ID", user.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
        entityManager.flush();
        entityManager.clear();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(payments.findById(failed.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(options.findById(option.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"IN_PROGRESS", "DONE"})
    void 승인중이거나_승인된_결제는_주문취소를_막는다(PaymentStatus state) throws Exception {
        Payment payment = savePayment(state);
        mockMvc.perform(post("/api/orders/{id}/cancel", order.getId()).header("X-USER-ID", user.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_STATE_CONFLICT"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(payment.getStatus()).isEqualTo(state);
        assertThat(option.getStockQuantity()).isEqualTo(7);
    }

    private void assertPreparationConflict(String code) throws Exception {
        mockMvc.perform(post("/api/orders/{id}/payments", order.getId()).header("X-USER-ID", user.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));
    }

    private Payment savePayment(PaymentStatus status) {
        Payment payment = new Payment(order, UUID.randomUUID().toString(), order.getTotalAmount());
        if (status == PaymentStatus.EXPIRED) {
            payment.expire();
        } else if (status != PaymentStatus.READY) {
            payment.startApproval(UUID.randomUUID().toString());
            if (status == PaymentStatus.FAILED) {
                payment.fail("DECLINED", "거절");
            } else if (status == PaymentStatus.DONE || status == PaymentStatus.CANCELED) {
                payment.complete(LocalDateTime.of(2026, 9, 20, 12, 0, 1));
                if (status == PaymentStatus.CANCELED) payment.cancel();
            }
        }
        return payments.save(payment);
    }
}
