package com.aurelia.catalog.application;

import com.aurelia.catalog.domain.Collection;
import com.aurelia.catalog.domain.Product;
import com.aurelia.catalog.domain.ProductQuery;
import com.aurelia.catalog.infrastructure.CatalogRepository;
import com.aurelia.catalog.infrastructure.InventoryClient;
import com.aurelia.platform.web.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final CatalogRepository repo;
    private final InventoryClient inventory;

    public CatalogService(CatalogRepository repo, InventoryClient inventory) {
        this.repo = repo;
        this.inventory = inventory;
    }

    public List<Collection> collections() {
        return repo.collections();
    }

    public record Facets(List<ColorFacet> colors, List<ValueFacet> fabrics, long minPricePaise, long maxPricePaise) {}
    public record ColorFacet(String family, String hex, int count) {}
    public record ValueFacet(String value, int count) {}
    public record Listing(Collection collection, List<ProductView> items, int page, int size, int total,
                          int totalPages, Facets facets, boolean availabilityKnown) {}

    /** A product joined with availability for display. {@code null} availability = unknown. */
    public record ProductView(Product product, Map<String, Boolean> variantInStock, Boolean inStock,
                              boolean bulkContext) {
        public long displayPricePaise() {
            return bulkContext && product.bulkPricePaise() != null ? product.bulkPricePaise() : product.pricePaise();
        }
    }

    public Listing list(ProductQuery q) {
        Collection collection = null;
        if (q.collection() != null && !q.collection().isBlank() && !"all".equals(q.collection())) {
            collection = repo.collection(q.collection())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND", "Collection not found."));
        }
        boolean bulk = collection != null && collection.bulk();
        List<Product> candidates = repo.candidates(collection, q.text());
        Optional<Map<String, Integer>> stock = inventory.availability(
                candidates.stream().flatMap(p -> p.variants().stream()).map(Product.Variant::sku).toList());
        List<ProductView> views = candidates.stream().map(p -> view(p, stock, bulk)).toList();

        Facets facets = facets(views);
        Predicate<ProductView> filter = v -> true;
        if (!q.colors().isEmpty()) {
            filter = filter.and(v -> v.product().variants().stream().anyMatch(x -> q.colors().contains(x.colorFamily())));
        }
        if (!q.fabrics().isEmpty()) {
            filter = filter.and(v -> q.fabrics().contains(v.product().fabric()));
        }
        if (q.minPricePaise() != null) filter = filter.and(v -> v.displayPricePaise() >= q.minPricePaise());
        if (q.maxPricePaise() != null) filter = filter.and(v -> v.displayPricePaise() <= q.maxPricePaise());
        if (q.inStockOnly()) {
            if (stock.isEmpty()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AVAILABILITY_UNAVAILABLE",
                        "Stock availability is temporarily unavailable. Remove the in-stock filter or retry.");
            }
            filter = filter.and(v -> Boolean.TRUE.equals(v.inStock()));
        }
        List<ProductView> filtered = views.stream().filter(filter).sorted(comparator(q.sort())).toList();
        int total = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) q.size()));
        int from = Math.min((q.page() - 1) * q.size(), total);
        int to = Math.min(from + q.size(), total);
        return new Listing(collection, filtered.subList(from, to), q.page(), q.size(), total, totalPages, facets,
                stock.isPresent());
    }

    public record Detail(ProductView product, List<ProductView> related, boolean availabilityKnown) {}

    public Detail detail(String slug) {
        Product p = repo.bySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found."));
        List<Product> related = repo.candidates(repo.collection(p.collectionSlug()).orElse(null), null).stream()
                .filter(r -> !r.id().equals(p.id())).limit(4).toList();
        var all = Stream.concat(Stream.of(p), related.stream()).flatMap(x -> x.variants().stream())
                .map(Product.Variant::sku).toList();
        var stock = inventory.availability(all);
        return new Detail(view(p, stock, false), related.stream().map(r -> view(r, stock, false)).toList(),
                stock.isPresent());
    }

    public List<ProductView> bySlugs(List<String> slugs) {
        List<Product> products = repo.bySlugs(slugs);
        var stock = inventory.availability(products.stream().flatMap(p -> p.variants().stream())
                .map(Product.Variant::sku).toList());
        return products.stream().map(p -> view(p, stock, false)).toList();
    }

    public record SkuInfo(String sku, String productSlug, String productName, String colorName, String colorHex,
                          String accentHex, String motif, String fabric, long unitPricePaise,
                          Long bulkUnitPricePaise, boolean bulkEligible, int maxPerOrder, boolean active) {}

    /** Authoritative price and eligibility lookup for cart/checkout. Unknown SKUs are omitted. */
    public List<SkuInfo> lookupSkus(List<String> skus) {
        var inactive = repo.inactiveSkus(skus);
        return repo.bySkus(skus).stream().flatMap(p -> p.variants().stream()
                        .filter(v -> skus.contains(v.sku()))
                        .map(v -> new SkuInfo(v.sku(), p.slug(), p.name(), v.colorName(), v.colorHex(), v.accentHex(),
                                p.motif(), p.fabric(), p.pricePaise(), p.bulkPricePaise(), p.bulkEligible(),
                                p.maxPerOrder(), !inactive.contains(v.sku()))))
                .toList();
    }

    private static ProductView view(Product p, Optional<Map<String, Integer>> stock, boolean bulk) {
        if (stock.isEmpty()) {
            return new ProductView(p, Map.of(), null, bulk);
        }
        Map<String, Boolean> byVariant = new TreeMap<>();
        p.variants().forEach(v -> byVariant.put(v.sku(), stock.get().getOrDefault(v.sku(), 0) > 0));
        return new ProductView(p, byVariant, byVariant.containsValue(true), bulk);
    }

    private static Facets facets(List<ProductView> views) {
        Map<String, int[]> colorCounts = new TreeMap<>();
        Map<String, String> colorHex = new TreeMap<>();
        Map<String, int[]> fabricCounts = new TreeMap<>();
        long min = Long.MAX_VALUE, max = 0;
        for (ProductView v : views) {
            v.product().variants().stream().map(Product.Variant::colorFamily).distinct().forEach(f ->
                    colorCounts.computeIfAbsent(f, k -> new int[1])[0]++);
            v.product().variants().forEach(x -> colorHex.putIfAbsent(x.colorFamily(), x.colorHex()));
            fabricCounts.computeIfAbsent(v.product().fabric(), k -> new int[1])[0]++;
            min = Math.min(min, v.displayPricePaise());
            max = Math.max(max, v.displayPricePaise());
        }
        return new Facets(
                colorCounts.entrySet().stream().map(e -> new ColorFacet(e.getKey(), colorHex.get(e.getKey()), e.getValue()[0])).toList(),
                fabricCounts.entrySet().stream().map(e -> new ValueFacet(e.getKey(), e.getValue()[0])).toList(),
                views.isEmpty() ? 0 : min, max);
    }

    private static Comparator<ProductView> comparator(ProductQuery.Sort sort) {
        Comparator<ProductView> byNewest = Comparator.comparing((ProductView v) -> v.product().createdAt()).reversed();
        return switch (sort) {
            case NEWEST -> byNewest;
            case PRICE_ASC -> Comparator.comparingLong(ProductView::displayPricePaise).thenComparing(byNewest);
            case PRICE_DESC -> Comparator.comparingLong(ProductView::displayPricePaise).reversed().thenComparing(byNewest);
            case NAME_ASC -> Comparator.comparing(v -> v.product().name());
            case FEATURED -> Comparator.comparing((ProductView v) -> v.product().featuredRank(),
                    Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byNewest);
        };
    }
}
