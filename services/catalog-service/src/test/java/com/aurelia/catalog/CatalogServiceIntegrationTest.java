package com.aurelia.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.aurelia.catalog.application.CatalogService;
import com.aurelia.catalog.domain.ProductQuery;
import com.aurelia.catalog.infrastructure.InventoryClient;
import com.aurelia.platform.web.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "platform.security.gateway-token=test-gateway-token-0123456789abcdef",
        "platform.security.service-token=test-service-token-0123456789abcdef"})
@Testcontainers(disabledWithoutDocker = true)
class CatalogServiceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CatalogService service;
    @MockitoBean InventoryClient inventory;

    ProductQuery q(String collection, Set<String> colors, String text, ProductQuery.Sort sort, boolean inStock) {
        return new ProductQuery(collection, colors, Set.of(), null, null, inStock, text, sort, 1, 48);
    }

    @Test
    void filtersSortAndFacets() {
        when(inventory.availability(anyList())).thenAnswer(inv -> {
            List<String> skus = inv.getArgument(0);
            var m = new HashMap<String, Integer>();
            skus.forEach(s -> m.put(s, s.equals("ARANYA-01") ? 0 : 5));
            return Optional.of(m);
        });
        var silk = service.list(q("kanjivaram-silks", Set.of(), null, ProductQuery.Sort.PRICE_ASC, false));
        assertThat(silk.total()).isEqualTo(4);
        assertThat(silk.items()).extracting(v -> v.displayPricePaise()).isSorted();
        assertThat(silk.facets().colors()).isNotEmpty();

        var maroon = service.list(q(null, Set.of("maroon"), null, ProductQuery.Sort.FEATURED, false));
        assertThat(maroon.items()).allMatch(v -> v.product().variants().stream().anyMatch(x -> x.colorFamily().equals("maroon")));

        var bulk = service.list(q("bulk-orders", Set.of(), null, ProductQuery.Sort.FEATURED, false));
        assertThat(bulk.items()).isNotEmpty().allMatch(v -> v.product().bulkEligible() && v.displayPricePaise() == v.product().bulkPricePaise());

        var search = service.list(q(null, Set.of(), "linen", ProductQuery.Sort.FEATURED, false));
        assertThat(search.items()).extracting(v -> v.product().fabric()).contains("Linen");
    }

    @Test
    void unknownCollectionAndUnavailableStockFilter() {
        assertThatThrownBy(() -> service.list(q("nope", Set.of(), null, ProductQuery.Sort.FEATURED, false)))
                .isInstanceOf(ApiException.class);
        when(inventory.availability(anyList())).thenReturn(Optional.empty());
        var degraded = service.list(q(null, Set.of(), null, ProductQuery.Sort.FEATURED, false));
        assertThat(degraded.availabilityKnown()).isFalse();
        assertThatThrownBy(() -> service.list(q(null, Set.of(), null, ProductQuery.Sort.FEATURED, true)))
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void skuLookupReturnsAuthoritativePrices() {
        var info = service.lookupSkus(List.of("KORA-01", "UNKNOWN-99"));
        assertThat(info).hasSize(1);
        assertThat(info.getFirst().unitPricePaise()).isEqualTo(245_000);
        assertThat(info.getFirst().bulkUnitPricePaise()).isEqualTo(215_000);
        assertThat(info.getFirst().bulkEligible()).isTrue();
    }
}
