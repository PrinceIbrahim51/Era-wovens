package com.aurelia.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelia.inventory.application.InventoryService;
import com.aurelia.inventory.application.InventoryService.Line;
import com.aurelia.inventory.domain.ReservationStatus;
import com.aurelia.platform.web.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "platform.security.gateway-token=test-gateway-token-0123456789abcdef",
        "platform.security.service-token=test-service-token-0123456789abcdef",
        "inventory.reservation.expiry-sweep-interval=PT1H"})
@Testcontainers(disabledWithoutDocker = true)
class InventoryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired InventoryService service;
    @Autowired JdbcClient jdbc;

    String sku;

    @BeforeEach
    void setUp() {
        sku = "TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.sql("INSERT INTO stock_levels (sku, on_hand, reserved) VALUES (:s, 5, 0)").param("s", sku).update();
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        int threads = 25;
        var success = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    try {
                        service.reserve(UUID.randomUUID().toString(), List.of(new Line(sku, 1)), null);
                        success.incrementAndGet();
                    } catch (ApiException expected) {
                        // insufficient stock
                    }
                    return null;
                });
            }
            start.countDown();
        }
        assertThat(success.get()).isEqualTo(5);
        assertThat(service.availability(List.of(sku))).containsEntry(sku, 0);
    }

    @Test
    void reservationIsAllOrNothingAndIdempotent() {
        String other = sku + "B";
        jdbc.sql("INSERT INTO stock_levels (sku, on_hand, reserved) VALUES (:s, 1, 0)").param("s", other).update();
        String ref = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.reserve(ref, List.of(new Line(sku, 2), new Line(other, 2)), null))
                .isInstanceOf(ApiException.class).hasMessageContaining("enough stock");
        assertThat(service.availability(List.of(sku, other))).containsEntry(sku, 5).containsEntry(other, 1);

        var first = service.reserve(ref, List.of(new Line(sku, 2)), null);
        var replay = service.reserve(ref, List.of(new Line(sku, 2)), null);
        assertThat(replay.lines()).isEqualTo(first.lines());
        assertThat(service.availability(List.of(sku))).containsEntry(sku, 3);
    }

    @Test
    void commitDecrementsOnHandAndReleaseIsRejectedAfterCommit() {
        String ref = UUID.randomUUID().toString();
        service.reserve(ref, List.of(new Line(sku, 2)), null);
        assertThat(service.commit(ref).status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(service.commit(ref).status()).isEqualTo(ReservationStatus.COMMITTED); // idempotent
        assertThatThrownBy(() -> service.release(ref, ReservationStatus.RELEASED)).isInstanceOf(ApiException.class);
        Integer onHand = jdbc.sql("SELECT on_hand FROM stock_levels WHERE sku = :s").param("s", sku).query(Integer.class).single();
        assertThat(onHand).isEqualTo(3);
    }

    @Test
    void expiredReservationsAreReleased() {
        String ref = UUID.randomUUID().toString();
        service.reserve(ref, List.of(new Line(sku, 4)), Duration.ofSeconds(60));
        jdbc.sql("UPDATE stock_reservations SET expires_at = now() - interval '1 minute' WHERE reference = :r")
                .param("r", ref).update();
        service.expireDue(100);
        assertThat(service.find(ref).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(service.availability(List.of(sku))).containsEntry(sku, 5);
        assertThatThrownBy(() -> service.commit(ref)).isInstanceOf(ApiException.class);
    }
}
