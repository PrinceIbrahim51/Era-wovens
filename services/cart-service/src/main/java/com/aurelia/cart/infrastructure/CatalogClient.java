package com.aurelia.cart.infrastructure;

import com.aurelia.platform.client.InternalClients;
import com.aurelia.platform.web.UpstreamException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CatalogClient {
    private final RestClient client;

    public CatalogClient(InternalClients clients, @Value("${services.catalog-url}") String url) {
        this.client = clients.create(url);
    }

    public record SkuInfo(String sku, String productSlug, String productName, String colorName, String colorHex,
                          String accentHex, String motif, String fabric, long unitPricePaise, Long bulkUnitPricePaise,
                          boolean bulkEligible, int maxPerOrder, boolean active) {}
    record Lookup(List<String> skus) {}
    record LookupResponse(List<SkuInfo> items) {}

    public Map<String, SkuInfo> lookup(List<String> skus) {
        if (skus.isEmpty()) return Map.of();
        try {
            var res = client.post().uri("/internal/v1/catalog/skus/lookup").body(new Lookup(skus)).retrieve()
                    .body(LookupResponse.class);
            return res.items().stream().collect(Collectors.toMap(SkuInfo::sku, Function.identity()));
        } catch (RestClientException e) {
            throw new UpstreamException("catalog lookup failed", e);
        }
    }
}
