package com.aurelia.catalog.controller;

import com.aurelia.catalog.application.CatalogService;
import com.aurelia.catalog.application.CatalogService.ProductView;
import com.aurelia.catalog.domain.Collection;
import com.aurelia.catalog.domain.Product;
import com.aurelia.catalog.domain.ProductQuery;
import com.aurelia.platform.web.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private static final java.util.regex.Pattern SLUG = java.util.regex.Pattern.compile("^[a-z0-9-]{1,120}$");
    private static final java.util.regex.Pattern COLOR = java.util.regex.Pattern.compile("^[a-z]{1,20}$");
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    public record CollectionDto(String slug, String name, String description, boolean bulk) {
        static CollectionDto of(Collection c) { return c == null ? null : new CollectionDto(c.slug(), c.name(), c.description(), c.bulk()); }
    }
    public record VariantDto(String sku, String colorName, String colorFamily, String colorHex, String accentHex, Boolean inStock) {}
    public record ProductDto(String slug, String name, CollectionRef collection, String fabric, String craft,
                             String occasion, long pricePaise, Long bulkPricePaise, boolean bulkEligible,
                             long displayPricePaise, String motif, boolean isNew, Boolean inStock,
                             List<VariantDto> variants, String description, Integer maxPerOrder) {}
    public record CollectionRef(String slug, String name) {}
    public record ListingDto(CollectionDto collection, List<ProductDto> items, int page, int size, int total,
                             int totalPages, CatalogService.Facets facets, boolean availabilityKnown) {}
    public record DetailDto(ProductDto product, List<ProductDto> related, boolean availabilityKnown) {}

    @GetMapping("/collections")
    public ResponseEntity<List<CollectionDto>> collections() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                .body(service.collections().stream().map(CollectionDto::of).toList());
    }

    @GetMapping("/products")
    public ListingDto products(
            @RequestParam(required = false) @Pattern(regexp = "^[a-z0-9-]{1,80}$") String collection,
            @RequestParam(required = false) @Size(max = 200) String color,
            @RequestParam(required = false) @Size(max = 300) String fabric,
            @RequestParam(required = false) @Min(0) @Max(10_000_000) Long minPrice,
            @RequestParam(required = false) @Min(0) @Max(10_000_000) Long maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 20) String sort,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(48) int size) {
        Set<String> colors = split(color, 12);
        if (!colors.stream().allMatch(c -> COLOR.matcher(c).matches())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid colour filter.");
        }
        Set<String> fabrics = split(fabric, 12);
        var query = new ProductQuery(collection, colors, fabrics,
                minPrice == null ? null : minPrice * 100, maxPrice == null ? null : maxPrice * 100,
                inStock, q == null ? null : q.strip(), ProductQuery.Sort.parse(sort), page, size);
        var l = service.list(query);
        return new ListingDto(CollectionDto.of(l.collection()), l.items().stream().map(v -> dto(v, false)).toList(),
                l.page(), l.size(), l.total(), l.totalPages(), l.facets(), l.availabilityKnown());
    }

    @GetMapping("/products/{slug}")
    public DetailDto product(@PathVariable String slug) {
        requireSlug(slug);
        var d = service.detail(slug);
        return new DetailDto(dto(d.product(), true), d.related().stream().map(v -> dto(v, false)).toList(),
                d.availabilityKnown());
    }

    /** Batch read for client-side wishlists (max 50 slugs). */
    @GetMapping("/products/batch")
    public List<ProductDto> batch(@RequestParam @Size(max = 2000) String slugs) {
        List<String> list = split(slugs, 50).stream().toList();
        list.forEach(this::requireSlug);
        return service.bySlugs(list).stream().map(v -> dto(v, false)).toList();
    }

    private void requireSlug(String slug) {
        if (!SLUG.matcher(slug).matches()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found.");
        }
    }

    private static Set<String> split(String csv, int max) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> set = Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .limit(max).collect(Collectors.toCollection(LinkedHashSet::new));
        return Set.copyOf(set);
    }

    static ProductDto dto(ProductView v, boolean detail) {
        Product p = v.product();
        Map<String, Boolean> stock = v.variantInStock();
        return new ProductDto(p.slug(), p.name(), new CollectionRef(p.collectionSlug(), p.collectionName()),
                p.fabric(), p.craft(), p.occasion(), p.pricePaise(), p.bulkPricePaise(), p.bulkEligible(),
                v.displayPricePaise(), p.motif(), p.isNew(), v.inStock(),
                p.variants().stream().map(x -> new VariantDto(x.sku(), x.colorName(), x.colorFamily(), x.colorHex(),
                        x.accentHex(), stock.isEmpty() ? null : stock.get(x.sku()))).toList(),
                detail ? p.description() : null, detail ? p.maxPerOrder() : null);
    }
}
