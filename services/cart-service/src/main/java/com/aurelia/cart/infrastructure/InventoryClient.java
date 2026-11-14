package com.aurelia.cart.infrastructure;

import com.aurelia.platform.client.InternalClients;
import com.aurelia.platform.web.UpstreamException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class InventoryClient {
    private final RestClient client;

    public InventoryClient(InternalClients clients, @Value("${services.inventory-url}") String url) {
        this.client = clients.create(url);
    }

    record Req(List<String> skus) {}
    record Res(Map<String, Integer> available) {}

    public Map<String, Integer> availability(List<String> skus) {
        if (skus.isEmpty()) return Map.of();
        try {
            return client.post().uri("/internal/v1/inventory/availability").body(new Req(skus)).retrieve()
                    .body(Res.class).available();
        } catch (RestClientException e) {
            throw new UpstreamException("inventory availability failed", e);
        }
    }
}
