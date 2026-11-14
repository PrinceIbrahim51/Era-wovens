package com.aurelia.checkout.infrastructure;

import com.aurelia.checkout.domain.CartType;
import com.aurelia.checkout.domain.PaymentMethod;
import com.aurelia.platform.client.InternalClients;
import com.aurelia.platform.client.Upstream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Typed clients for the sibling services checkout orchestrates. Each service remains the owner of its data. */
@Component
public class ServiceClients {
    private final RestClient catalog;
    private final RestClient inventory;
    private final RestClient cart;
    private final RestClient order;
    private final RestClient cod;

    public ServiceClients(InternalClients clients,
                          @Value("${services.catalog-url}") String catalogUrl,
                          @Value("${services.inventory-url}") String inventoryUrl,
                          @Value("${services.cart-url}") String cartUrl,
                          @Value("${services.order-url}") String orderUrl,
                          @Value("${services.cod-url}") String codUrl) {
        this.catalog = clients.create(catalogUrl);
        this.inventory = clients.create(inventoryUrl);
        this.cart = clients.create(cartUrl);
        this.order = clients.create(orderUrl);
        this.cod = clients.create(codUrl);
    }

    // ---------- cart ----------
    public record CartLine(String sku, int quantity) {}
    public record RawCart(CartType cartType, long version, List<CartLine> lines) {}
    record Consume(CartType cartType, List<CartLine> lines) {}

    public RawCart cart(String owner, CartType type) {
        return Upstream.call("cart read", () -> cart.get().uri("/internal/v1/carts/{o}?type={t}", owner, type)
                .retrieve().body(RawCart.class));
    }

    public void consumeCart(String owner, CartType type, List<CartLine> lines) {
        Upstream.run("cart consume", () -> cart.post().uri("/internal/v1/carts/{o}/consume", owner)
                .body(new Consume(type, lines)).retrieve().toBodilessEntity());
    }

    // ---------- catalog ----------
    public record SkuInfo(String sku, String productSlug, String productName, String colorName, String colorHex,
                          String accentHex, String motif, String fabric, long unitPricePaise, Long bulkUnitPricePaise,
                          boolean bulkEligible, int maxPerOrder, boolean active) {}
    record Lookup(List<String> skus) {}
    record LookupResponse(List<SkuInfo> items) {}

    public Map<String, SkuInfo> skus(List<String> skus) {
        var res = Upstream.call("catalog lookup", () -> catalog.post().uri("/internal/v1/catalog/skus/lookup")
                .body(new Lookup(skus)).retrieve().body(LookupResponse.class));
        return res.items().stream().collect(Collectors.toMap(SkuInfo::sku, Function.identity()));
    }

    // ---------- inventory ----------
    record Availability(List<String> skus) {}
    record AvailabilityResponse(Map<String, Integer> available) {}
    record Reserve(String reference, List<CartLine> lines, int ttlSeconds) {}

    public Map<String, Integer> availability(List<String> skus) {
        return Upstream.call("inventory availability", () -> inventory.post().uri("/internal/v1/inventory/availability")
                .body(new Availability(skus)).retrieve().body(AvailabilityResponse.class)).available();
    }

    public void reserve(UUID orderId, List<CartLine> lines, int ttlSeconds) {
        Upstream.run("inventory reserve", () -> inventory.post().uri("/internal/v1/inventory/reservations")
                .body(new Reserve(orderId.toString(), lines, ttlSeconds)).retrieve().toBodilessEntity());
    }

    public void release(UUID orderId) {
        Upstream.run("inventory release", () -> inventory.post().uri("/internal/v1/inventory/reservations/{r}/release", orderId)
                .retrieve().toBodilessEntity());
    }

    // ---------- cod ----------
    public record CodEligibility(boolean eligible, long feePaise, List<String> reasons) {}
    record CodEligibilityRequest(String ownerHash, String pincode, long orderValuePaise, CartType cartType, String phone) {}
    record CodConfirmRequest(UUID orderId, String ownerHash, String pincode, long orderValuePaise, long feePaise,
                             CartType cartType, String phone) {}

    public CodEligibility codEligibility(String owner, String pincode, long orderValuePaise, CartType type, String phone) {
        return Upstream.call("cod eligibility", () -> cod.post().uri("/internal/v1/cod/eligibility")
                .body(new CodEligibilityRequest(owner, pincode, orderValuePaise, type, phone)).retrieve()
                .body(CodEligibility.class));
    }

    public void codConfirm(UUID orderId, String owner, String pincode, long orderValuePaise, long feePaise,
                           CartType type, String phone) {
        Upstream.run("cod confirm", () -> cod.post().uri("/internal/v1/cod/confirmations")
                .body(new CodConfirmRequest(orderId, owner, pincode, orderValuePaise, feePaise, type, phone))
                .retrieve().toBodilessEntity());
    }

    // ---------- order ----------
    public record OrderLine(String sku, String productName, String colorName, long unitPricePaise, int quantity,
                            long lineTotalPaise, long discountPaise, int taxRateBp, long taxIncludedPaise) {}
    public record Contact(String fullName, String email, String phone) {}
    public record Address(String line1, String line2, String landmark, String city, String state, String pincode) {}
    public record CreateOrder(String idempotencyKey, String ownerHash, CartType cartType, PaymentMethod paymentMethod,
                              Contact contact, Address shippingAddress, List<OrderLine> lines, long subtotalPaise,
                              long discountPaise, long shippingPaise, long codFeePaise, long taxIncludedPaise,
                              long totalPaise, String currency, String couponCode) {}
    public record OrderRef(UUID id, String orderNumber, String status) {}
    record Transition(String to, String reason, String actor) {}

    public OrderRef createOrder(CreateOrder req) {
        return Upstream.call("order create", () -> order.post().uri("/internal/v1/orders").body(req).retrieve()
                .body(OrderRef.class));
    }

    public OrderRef transition(UUID orderId, String to, String reason) {
        return Upstream.call("order transition", () -> order.post().uri("/internal/v1/orders/{id}/transitions", orderId)
                .body(new Transition(to, reason, "checkout-service")).retrieve().body(OrderRef.class));
    }
}
