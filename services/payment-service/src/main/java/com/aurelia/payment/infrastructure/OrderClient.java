package com.aurelia.payment.infrastructure;

import com.aurelia.platform.client.InternalClients;
import com.aurelia.platform.client.Upstream;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderClient {
    private final RestClient client;

    public OrderClient(InternalClients clients, @Value("${services.order-url}") String url) {
        this.client = clients.create(url);
    }

    public record InternalOrder(UUID id, String orderNumber, String ownerHash, String status, String paymentMethod,
                                String paymentStatus, long totalPaise, String currency, String contactName,
                                String contactEmail, String contactPhone, Instant expiresAt) {}
    public record OrderRef(UUID id, String orderNumber, String status) {}
    record Capture(String providerPaymentId, long amountPaise, String currency) {}

    public InternalOrder get(UUID id) {
        return Upstream.call("order read", () -> client.get().uri("/internal/v1/orders/{id}", id).retrieve()
                .body(InternalOrder.class));
    }

    public OrderRef paymentCaptured(UUID id, String paymentId, long amountPaise, String currency) {
        return Upstream.call("order capture", () -> client.post().uri("/internal/v1/orders/{id}/payment-captured", id)
                .body(new Capture(paymentId, amountPaise, currency)).retrieve().body(OrderRef.class));
    }
}
