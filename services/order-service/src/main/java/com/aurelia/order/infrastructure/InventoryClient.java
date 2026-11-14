package com.aurelia.order.infrastructure;

import com.aurelia.platform.client.InternalClients;
import com.aurelia.platform.client.Upstream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {
    private final RestClient client;

    public InventoryClient(InternalClients clients, @Value("${services.inventory-url}") String url) {
        this.client = clients.create(url);
    }

    public void commit(UUID orderId) {
        Upstream.run("inventory commit", () -> client.post().uri("/internal/v1/inventory/reservations/{r}/commit", orderId)
                .retrieve().toBodilessEntity());
    }

    public void release(UUID orderId) {
        Upstream.run("inventory release", () -> client.post().uri("/internal/v1/inventory/reservations/{r}/release", orderId)
                .retrieve().toBodilessEntity());
    }
}
