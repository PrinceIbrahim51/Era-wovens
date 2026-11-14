package com.aurelia.catalog.infrastructure;

import com.aurelia.platform.client.InternalClients;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Reads availability from inventory-service (the stock owner). Degrades to "unknown" on failure. */
@Component
public class InventoryClient {
    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);
    private final RestClient client;

    public InventoryClient(InternalClients clients, @Value("${services.inventory-url}") String baseUrl) {
        this.client = clients.create(baseUrl);
    }

    record AvailabilityRequest(List<String> skus) {}
    record AvailabilityResponse(Map<String, Integer> available) {}

    public Optional<Map<String, Integer>> availability(List<String> skus) {
        if (skus.isEmpty()) return Optional.of(Map.of());
        try {
            var res = client.post().uri("/internal/v1/inventory/availability")
                    .body(new AvailabilityRequest(skus)).retrieve().body(AvailabilityResponse.class);
            return Optional.ofNullable(res).map(AvailabilityResponse::available);
        } catch (RestClientException e) {
            log.warn("Inventory availability unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
