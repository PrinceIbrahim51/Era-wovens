package com.aurelia.catalog.infrastructure;

import com.aurelia.catalog.domain.Collection;
import com.aurelia.catalog.domain.Product;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** All queries are parameterised; no user input is concatenated into SQL. */
@Repository
public class CatalogRepository {
    private static final int MAX_CANDIDATES = 500;
    private static final String SELECT = """
            SELECT p.id, p.slug, p.name, p.description, c.slug AS collection_slug, c.name AS collection_name,
                   p.fabric, p.craft, p.occasion, p.price_paise, p.bulk_price_paise, p.bulk_eligible, p.motif,
                   p.is_new, p.featured_rank, p.max_per_order, p.created_at
            FROM products p JOIN collections c ON c.id = p.collection_id
            """;

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Collection> collections() {
        return jdbc.sql("SELECT slug, name, description, is_bulk FROM collections ORDER BY sort_order")
                .query((rs, i) -> new Collection(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)))
                .list();
    }

    public Optional<Collection> collection(String slug) {
        return jdbc.sql("SELECT slug, name, description, is_bulk FROM collections WHERE slug = :slug")
                .param("slug", slug)
                .query((rs, i) -> new Collection(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)))
                .optional();
    }

    /**
     * Candidate products for a listing, before colour/fabric/price/stock refinement.
     * @param collection collection to scope to (null = all); bulk collections select bulk-eligible products
     * @param text optional full-text query (websearch syntax, parsed safely by PostgreSQL)
     */
    public List<Product> candidates(Collection collection, String text) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE p.active");
        Map<String, Object> params = new HashMap<>();
        if (collection != null) {
            if (collection.bulk()) {
                sql.append(" AND p.bulk_eligible");
            } else {
                sql.append(" AND c.slug = :collection");
                params.put("collection", collection.slug());
            }
        }
        if (text != null && !text.isBlank()) {
            sql.append("""
                     AND (p.search_vector @@ websearch_to_tsquery('english', :q)
                          OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.active
                                     AND (lower(v.color_family) = lower(:qRaw) OR v.color_name ILIKE :qLike)))
                    """);
            params.put("q", text);
            params.put("qRaw", text.trim());
            params.put("qLike", "%" + escapeLike(text.trim()) + "%");
        }
        sql.append(" ORDER BY p.created_at DESC LIMIT ").append(MAX_CANDIDATES);
        var spec = jdbc.sql(sql.toString());
        for (var e : params.entrySet()) spec = spec.param(e.getKey(), e.getValue());
        return attachVariants(spec.query(this::mapProduct).list());
    }

    public Optional<Product> bySlug(String slug) {
        return jdbc.sql(SELECT + " WHERE p.active AND p.slug = :slug").param("slug", slug)
                .query(this::mapProduct).optional()
                .map(p -> attachVariants(List.of(p)).getFirst());
    }

    public List<Product> bySlugs(List<String> slugs) {
        if (slugs.isEmpty()) return List.of();
        return attachVariants(jdbc.sql(SELECT + " WHERE p.active AND p.slug IN (:slugs)").param("slugs", slugs)
                .query(this::mapProduct).list());
    }

    public List<Product> bySkus(List<String> skus) {
        if (skus.isEmpty()) return List.of();
        return attachVariants(jdbc.sql(SELECT + """
                 WHERE p.id IN (SELECT product_id FROM product_variants WHERE sku IN (:skus))
                """).param("skus", skus).query(this::mapProductAnyStatus).list());
    }

    /** SKUs whose variant or product is inactive. */
    public List<String> inactiveSkus(List<String> skus) {
        if (skus.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT v.sku FROM product_variants v JOIN products p ON p.id = v.product_id
                WHERE v.sku IN (:skus) AND (NOT v.active OR NOT p.active)
                """).param("skus", skus).query(String.class).list();
    }

    private List<Product> attachVariants(List<Product> products) {
        if (products.isEmpty()) return products;
        List<UUID> ids = products.stream().map(Product::id).toList();
        Map<UUID, List<Product.Variant>> byProduct = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT product_id, sku, color_name, color_family, color_hex, accent_hex, position
                FROM product_variants WHERE product_id IN (:ids) AND active ORDER BY position
                """).param("ids", ids).query(rs -> {
            byProduct.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                    .add(new Product.Variant(rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5).trim(), rs.getString(6).trim(), rs.getInt(7)));
        });
        return products.stream()
                .map(p -> p.withVariants(Collections.unmodifiableList(byProduct.getOrDefault(p.id(), List.of()))))
                .toList();
    }

    private Product mapProductAnyStatus(ResultSet rs, int i) throws SQLException {
        return mapProduct(rs, i);
    }

    private Product mapProduct(ResultSet rs, int i) throws SQLException {
        long bulk = rs.getLong("bulk_price_paise");
        Long bulkPrice = rs.wasNull() ? null : bulk;
        int rank = rs.getInt("featured_rank");
        Integer featured = rs.wasNull() ? null : rank;
        return new Product(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                rs.getString("description"), rs.getString("collection_slug"), rs.getString("collection_name"),
                rs.getString("fabric"), rs.getString("craft"), rs.getString("occasion"), rs.getLong("price_paise"),
                bulkPrice, rs.getBoolean("bulk_eligible"), rs.getString("motif"), rs.getBoolean("is_new"), featured,
                rs.getInt("max_per_order"), rs.getTimestamp("created_at").toInstant(), List.of());
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
