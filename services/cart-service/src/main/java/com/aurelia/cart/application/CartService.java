package com.aurelia.cart.application;

import com.aurelia.cart.config.CartProperties;
import com.aurelia.cart.domain.CartRules;
import com.aurelia.cart.domain.CartType;
import com.aurelia.cart.infrastructure.CartRepository;
import com.aurelia.cart.infrastructure.CartRepository.ItemRow;
import com.aurelia.cart.infrastructure.CatalogClient;
import com.aurelia.cart.infrastructure.CatalogClient.SkuInfo;
import com.aurelia.cart.infrastructure.InventoryClient;
import com.aurelia.platform.web.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cart holds SKUs and quantities only. Prices shown here are a preview from the catalogue;
 * authoritative totals are produced by checkout-service.
 */
@Service
public class CartService {
    private final CartRepository repo;
    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final CartProperties props;

    public CartService(CartRepository repo, CatalogClient catalog, InventoryClient inventory, CartProperties props) {
        this.repo = repo;
        this.catalog = catalog;
        this.inventory = inventory;
        this.props = props;
    }

    public record LineView(UUID itemId, String sku, String productSlug, String productName, String colorName,
                           String colorHex, String accentHex, String motif, String fabric, int quantity,
                           int maxQuantity, Long unitPricePaise, Long lineTotalPaise, String issue) {}
    public record CartView(CartType cartType, List<LineView> lines, int lineCount, int units, long subtotalPaise,
                           CartRules.BulkStatus bulk, boolean readyForCheckout, String currency) {}
    public record Summary(int regularUnits, int bulkUnits) {}

    public CartView view(String owner, CartType type) {
        var cart = repo.find(owner, type);
        return render(type, cart.map(c -> repo.items(c.id())).orElse(List.of()));
    }

    public Summary summary(String owner) {
        int r = repo.find(owner, CartType.REGULAR).map(c -> repo.items(c.id()).stream().mapToInt(ItemRow::quantity).sum()).orElse(0);
        int b = repo.find(owner, CartType.BULK).map(c -> repo.items(c.id()).stream().mapToInt(ItemRow::quantity).sum()).orElse(0);
        return new Summary(r, b);
    }

    @Transactional
    public CartView add(String owner, CartType type, String sku, int quantity) {
        var cart = repo.lockOrCreate(owner, type, props.ttl());
        var items = repo.items(cart.id());
        int existing = items.stream().filter(i -> i.sku().equals(sku)).mapToInt(ItemRow::quantity).findFirst().orElse(0);
        if (existing == 0 && items.size() >= props.maxLines()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CART_LINE_LIMIT", "Your cart has reached its line limit.");
        }
        int target = existing + quantity;
        enforce(type, sku, target);
        repo.upsertQuantity(cart.id(), sku, target);
        repo.touch(cart.id(), props.ttl());
        return render(type, repo.items(cart.id()));
    }

    @Transactional
    public CartView update(String owner, UUID itemId, int quantity) {
        CartType type = ownedType(owner, itemId);
        var cart = repo.lockOrCreate(owner, type, props.ttl());
        var item = repo.item(cart.id(), itemId).orElseThrow(this::itemNotFound);
        enforce(type, item.sku(), quantity);
        repo.setQuantity(itemId, quantity);
        repo.touch(cart.id(), props.ttl());
        return render(type, repo.items(cart.id()));
    }

    @Transactional
    public CartView remove(String owner, UUID itemId) {
        CartType type = ownedType(owner, itemId);
        var cart = repo.lockOrCreate(owner, type, props.ttl());
        repo.delete(itemId);
        repo.touch(cart.id(), props.ttl());
        return render(type, repo.items(cart.id()));
    }

    // ---- internal API for checkout-service ----

    public record RawLine(String sku, int quantity) {}
    public record RawCart(CartType cartType, long version, List<RawLine> lines) {}

    public RawCart raw(String owner, CartType type) {
        return repo.find(owner, type)
                .map(c -> new RawCart(type, c.version(), repo.items(c.id()).stream().map(i -> new RawLine(i.sku(), i.quantity())).toList()))
                .orElse(new RawCart(type, 0, List.of()));
    }

    /** Removes ordered quantities after an order is placed; items added meanwhile are kept. */
    @Transactional
    public RawCart consume(String owner, CartType type, List<RawLine> ordered) {
        var cart = repo.lockOrCreate(owner, type, props.ttl());
        Map<String, Integer> orderedQty = new LinkedHashMap<>();
        ordered.forEach(l -> orderedQty.merge(l.sku(), l.quantity(), Integer::sum));
        for (ItemRow item : repo.items(cart.id())) {
            Integer q = orderedQty.get(item.sku());
            if (q == null) continue;
            if (item.quantity() <= q) repo.delete(item.id());
            else repo.setQuantity(item.id(), item.quantity() - q);
        }
        repo.touch(cart.id(), props.ttl());
        return raw(owner, type);
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    public void purgeExpired() {
        repo.deleteExpired();
    }

    // ---- helpers ----

    private CartType ownedType(String owner, UUID itemId) {
        return repo.cartTypeOfItem(owner, itemId).orElseThrow(this::itemNotFound);
    }

    private ApiException itemNotFound() {
        // Same response whether the item does not exist or belongs to someone else (no IDOR oracle).
        return new ApiException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "Cart item not found.");
    }

    private void enforce(CartType type, String sku, int quantity) {
        SkuInfo info = catalog.lookup(List.of(sku)).get(sku);
        Integer available = inventory.availability(List.of(sku)).getOrDefault(sku, 0);
        var facts = facts(info, available);
        String error = CartRules.validateLine(type, quantity, facts, props.bulk());
        if (error != null) {
            int max = info == null ? 0 : Math.min(CartRules.maxQuantity(type, facts, props.bulk()), Math.max(available, 0));
            throw new ApiException(error.equals("SKU_UNAVAILABLE") ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    error, message(error), Map.of("maxQuantity", max));
        }
    }

    private static CartRules.SkuFacts facts(SkuInfo info, Integer available) {
        if (info == null) return new CartRules.SkuFacts(false, false, false, 0, available);
        return new CartRules.SkuFacts(true, info.active(), info.bulkEligible(), info.maxPerOrder(), available);
    }

    private static String message(String code) {
        return switch (code) {
            case "SKU_UNAVAILABLE" -> "This item is no longer available.";
            case "NOT_BULK_ELIGIBLE" -> "This style is not part of the bulk-order collection.";
            case "QUANTITY_LIMIT_EXCEEDED" -> "That quantity exceeds the per-order limit for this item.";
            case "OUT_OF_STOCK" -> "This colour is currently out of stock.";
            case "INSUFFICIENT_STOCK" -> "Not enough stock for that quantity.";
            default -> "Invalid quantity.";
        };
    }

    private CartView render(CartType type, List<ItemRow> items) {
        List<String> skus = items.stream().map(ItemRow::sku).toList();
        Map<String, SkuInfo> info = catalog.lookup(skus);
        Map<String, Integer> stock = inventory.availability(skus);
        List<LineView> lines = new ArrayList<>();
        Map<String, Integer> quantities = new LinkedHashMap<>();
        long subtotal = 0;
        boolean allValid = true;
        for (ItemRow item : items) {
            SkuInfo s = info.get(item.sku());
            var facts = facts(s, stock.getOrDefault(item.sku(), 0));
            String issue = CartRules.validateLine(type, item.quantity(), facts, props.bulk());
            Long unit = s == null ? null : (type == CartType.BULK && s.bulkUnitPricePaise() != null ? s.bulkUnitPricePaise() : s.unitPricePaise());
            Long lineTotal = unit == null ? null : Math.multiplyExact(unit, (long) item.quantity());
            if (issue == null && lineTotal != null) subtotal = Math.addExact(subtotal, lineTotal);
            if (issue != null) allValid = false;
            quantities.put(item.sku(), item.quantity());
            lines.add(new LineView(item.id(), item.sku(), s == null ? null : s.productSlug(),
                    s == null ? "Unavailable item" : s.productName(), s == null ? null : s.colorName(),
                    s == null ? null : s.colorHex(), s == null ? null : s.accentHex(), s == null ? null : s.motif(),
                    s == null ? null : s.fabric(), item.quantity(),
                    s == null ? 0 : Math.min(CartRules.maxQuantity(type, facts, props.bulk()), Math.max(0, facts.available())),
                    unit, lineTotal, issue));
        }
        CartRules.BulkStatus bulk = type == CartType.BULK ? CartRules.bulkStatus(quantities, props.bulk()) : null;
        int units = quantities.values().stream().mapToInt(Integer::intValue).sum();
        boolean ready = !items.isEmpty() && allValid && (bulk == null || bulk.met());
        return new CartView(type, lines, lines.size(), units, subtotal, bulk, ready, "INR");
    }
}
