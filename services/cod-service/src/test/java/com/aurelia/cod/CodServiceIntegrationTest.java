package com.aurelia.cod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelia.cod.application.CodService;
import com.aurelia.platform.web.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "platform.security.gateway-token=test-gateway-token-0123456789abcdef",
        "platform.security.service-token=test-service-token-0123456789abcdef",
        "cod.phone-pepper=test-pepper-0123456789abcdef0123456789"})
@Testcontainers(disabledWithoutDocker = true)
class CodServiceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CodService service;

    @Test
    void confirmIsIdempotentAndCollectionReconciles() {
        UUID order = UUID.randomUUID();
        String owner = "b".repeat(64);
        var first = service.confirm(order, owner, "560001", 154_900, 4_900, "REGULAR", "9876543210");
        var again = service.confirm(order, owner, "560001", 154_900, 4_900, "REGULAR", "9876543210");
        assertThat(again).isEqualTo(first);
        assertThat(first.status()).isEqualTo("PENDING_COLLECTION");

        assertThatThrownBy(() -> service.recordOutcome(order, "COLLECTED", 100, "courier-1", null))
                .isInstanceOf(ApiException.class).hasMessageContaining("must equal");
        assertThat(service.recordOutcome(order, "COLLECTED", 154_900, "courier-1", "cash").status()).isEqualTo("COLLECTED");
        assertThatThrownBy(() -> service.recordOutcome(order, "RTO", 0, "courier-1", null))
                .hasMessageContaining("already recorded");
    }

    @Test
    void pendingLimitBlocksFurtherCodOrders() {
        String owner = "c".repeat(64);
        for (int i = 0; i < 3; i++) {
            service.confirm(UUID.randomUUID(), owner, "560001", 104_900, 4_900, "REGULAR", "9123456780");
        }
        assertThat(service.eligibility(owner, "560001", 100_000L, "REGULAR", "9123456780").reasons())
                .contains("TOO_MANY_PENDING_COD_ORDERS");
        assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), owner, "560001", 104_900, 4_900, "REGULAR", "9123456780"))
                .isInstanceOf(ApiException.class);
    }
}
