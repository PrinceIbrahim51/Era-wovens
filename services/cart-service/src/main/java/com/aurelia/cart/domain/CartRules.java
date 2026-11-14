package com.aurelia.cart.domain;

import com.aurelia.cart.config.CartProperties;
import java.util.List;
import java.util.Map;

/** Pure business rules for carts; unit-tested without infrastructure. */
public final class CartRules {
    private CartRules() {}

    public record SkuFacts(boolean exists, boolean active, boolean bulkEligible, int maxPerOrder, Integer available) {}

    /** @return an error code, or null when the quantity is acceptable for the line. */
    public static String validateLine(CartType type, int quantity, SkuFacts facts, CartProperties.Bulk bulk) {
        if (!facts.exists() || !facts.active()) return "SKU_UNAVAILABLE";
        if (type == CartType.BULK && !facts.bulkEligible()) return "NOT_BULK_ELIGIBLE";
        if (quantity < 1) return "INVALID_QUANTITY";
        int max = maxQuantity(type, facts, bulk);
        if (quantity > max) return "QUANTITY_LIMIT_EXCEEDED";
        if (facts.available() != null && quantity > facts.available()) {
            return facts.available() <= 0 ? "OUT_OF_STOCK" : "INSUFFICIENT_STOCK";
        }
        return null;
    }

    public static int maxQuantity(CartType type, SkuFacts facts, CartProperties.Bulk bulk) {
        return type == CartType.BULK ? bulk.maxLineQuantity() : facts.maxPerOrder();
    }

    public record BulkStatus(int minimumUnits, int units, boolean allowMixedSkus, boolean met, List<String> skusBelowMinimum) {}

    /** Bulk minimum: total units across SKUs, or per-SKU when mixing is disabled. */
    public static BulkStatus bulkStatus(Map<String, Integer> quantities, CartProperties.Bulk bulk) {
        int units = quantities.values().stream().mapToInt(Integer::intValue).sum();
        if (bulk.allowMixedSkus()) {
            return new BulkStatus(bulk.minimumUnits(), units, true, units >= bulk.minimumUnits(), List.of());
        }
        List<String> below = quantities.entrySet().stream().filter(e -> e.getValue() < bulk.minimumUnits())
                .map(Map.Entry::getKey).sorted().toList();
        return new BulkStatus(bulk.minimumUnits(), units, false, !quantities.isEmpty() && below.isEmpty(), below);
    }
}
