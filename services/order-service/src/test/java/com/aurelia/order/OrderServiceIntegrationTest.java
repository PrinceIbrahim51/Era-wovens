package com.aurelia.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.aurelia.order.application.OrderService;
import com.aurelia.order.domain.OrderStatus;
import com.aurelia.order.infrastructure.InventoryClient;
import com.aurelia.platform.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "platform.security.gateway-token=test-gateway-token-0123456789abcdef",
        "platform.security.service-token=test-service-token-0123456789abcdef",
        "spring.rabbitmq.username=guest", "spring.rabbitmq.password=guest",
        "orders.outbox-interval=PT1H", "orders.expiry-interval=PT1H"})
@Testcontainers(disabledWithoutDocker = true)
class OrderServiceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OrderService service;
    @Autowired JdbcClient jdbc;
    @MockitoBean InventoryClient inventory;

    OrderService.CreateOrder order(String key, String method) {
        var line = new OrderService.Line("SKU-1", "Saree", "Red", 100_000, 2, 200_000, 0, 500, 9_524);
        return new OrderService.CreateOrder(key, "a".repeat(64), "REGULAR", method,
                new OrderService.Contact("Asha Rao", "asha@example.com", "9876543210"),
                new OrderService.Address("12 MG Road", null, null, "Bengaluru", "Karnataka", "560001"),
                List.of(line), 200_000, 0, 9_900, 0, 9_524, 209_900, "INR", null);
    }

    @Test
    void createIsIdempotentAndValidatesSnapshot() {
        String key = UUID.randomUUID().toString();
        var a = service.create(order(key, "ONLINE"));
        var b = service.create(order(key, "ONLINE"));
        assertThat(a.id()).isEqualTo(b.id());
        assertThat(a.orderNumber()).matches("^ERA-[0-9]{6}-[A-Z0-9]{6}$");

        var tampered = order(UUID.randomUUID().toString(), "ONLINE");
        var bad = new OrderService.CreateOrder(tampered.idempotencyKey(), tampered.ownerHash(), "REGULAR", "ONLINE",
                tampered.contact(), tampered.shippingAddress(), tampered.lines(), 200_000, 0, 9_900, 0, 9_524, 100, "INR", null);
        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(ApiException.class);
    }

    @Test
    void onlinePaymentConfirmsOnceWithMatchingAmount() {
        var o = service.create(order(UUID.randomUUID().toString(), "ONLINE"));
        service.transition(o.id(), OrderStatus.PENDING_PAYMENT, "await", "test");
        assertThatThrownBy(() -> service.paymentCaptured(o.id(), "pay_ABC123456", 1, "INR"))
                .hasMessageContaining("does not match");
        var confirmed = service.paymentCaptured(o.id(), "pay_ABC123456", 209_900, "INR");
        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(service.paymentCaptured(o.id(), "pay_ABC123456", 209_900, "INR").status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> service.paymentCaptured(o.id(), "pay_OTHER12345", 209_900, "INR"))
                .hasMessageContaining("different captured payment");
        Integer outbox = jdbc.sql("SELECT count(*) FROM outbox WHERE aggregate_id = :id AND event_type = 'order.confirmed'")
                .param("id", o.id()).query(Integer.class).single();
        assertThat(outbox).isEqualTo(1);
    }

    @Test
    void illegalTransitionRejectedAndSnapshotsImmutable() {
        var o = service.create(order(UUID.randomUUID().toString(), "COD"));
        assertThatThrownBy(() -> service.transition(o.id(), OrderStatus.CONFIRMED, "skip", "test"))
                .isInstanceOf(ApiException.class).hasMessageContaining("cannot move");
        assertThatThrownBy(() -> jdbc.sql("UPDATE order_items SET unit_price_paise = 1 WHERE order_id = :id")
                .param("id", o.id()).update()).hasMessageContaining("immutable");
    }

    @Test
    void stockCommitFailureRoutesToReview() {
        var o = service.create(order(UUID.randomUUID().toString(), "COD"));
        service.transition(o.id(), OrderStatus.COD_CONFIRMED, "cod", "test");
        doThrow(new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_ACTIVE", "x")).when(inventory).commit(any());
        assertThat(service.transition(o.id(), OrderStatus.CONFIRMED, "confirm", "test").status())
                .isEqualTo(OrderStatus.REVIEW_REQUIRED);
    }

    @Test
    void lateCaptureAfterExpiryNeedsReview() {
        var o = service.create(order(UUID.randomUUID().toString(), "ONLINE"));
        service.transition(o.id(), OrderStatus.PENDING_PAYMENT, "await", "test");
        service.transition(o.id(), OrderStatus.EXPIRED, "timeout", "test");
        assertThat(service.paymentCaptured(o.id(), "pay_LATE1234567", 209_900, "INR").status())
                .isEqualTo(OrderStatus.REVIEW_REQUIRED);
    }
}
