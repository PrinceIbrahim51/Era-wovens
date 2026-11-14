package com.aurelia.catalog.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Product(
        UUID id, String slug, String name, String description,
        String collectionSlug, String collectionName,
        String fabric, String craft, String occasion,
        long pricePaise, Long bulkPricePaise, boolean bulkEligible,
        String motif, boolean isNew, Integer featuredRank, int maxPerOrder,
        Instant createdAt, List<Variant> variants) {

    public record Variant(String sku, String colorName, String colorFamily, String colorHex, String accentHex,
                          int position) {}

    public Product withVariants(List<Variant> v) {
        return new Product(id, slug, name, description, collectionSlug, collectionName, fabric, craft, occasion,
                pricePaise, bulkPricePaise, bulkEligible, motif, isNew, featuredRank, maxPerOrder, createdAt, v);
    }
}
