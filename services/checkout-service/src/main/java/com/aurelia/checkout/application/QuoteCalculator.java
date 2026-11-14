package com.aurelia.checkout.application;

import com.aurelia.checkout.config.CheckoutProperties;
import com.aurelia.checkout.domain.CartType;
import com.aurelia.checkout.domain.Coupon;
import com.aurelia.checkout.domain.Pincodes;
import com.aurelia.checkout.domain.PricingEngine;
import com.aurelia.checkout.infrastructure.CheckoutRepository;
import com.aurelia.checkout.infrastructure.ServiceClients;
import com.aurelia.checkout.infrastructure.ServiceClients.CartLine;
import com.aurelia.platform.web.ApiException;
import com.aurelia.platform.web.UpstreamException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Recomputes everything from owner data: cart contents (cart-service), prices and eligibility (catalog),
 * stock (inventory), coupon rules (local) and COD eligibility (cod-service). Client totals are never used.
 */
@Component
public class QuoteCalculator {
    private static final Logger log = LoggerFactory.getLogger(QuoteCalculator.class);
    static final int BULK_MAX_LINE_QTY = 500;

    private final ServiceClients clients;
    private final CheckoutRepository repo;
    private final CheckoutProperties props;
    private final PricingEngine engine;
    private final Clock clock;

    public QuoteCalculator(ServiceClients clients, CheckoutRepository repo, CheckoutProperties props, Clock clock) {
        this.clients = clients;
        this.repo = repo;
        this.props = props;
        this.engine = new PricingEngine(props.tax(), props.shipping());
        this.clock = clock;
    }

    public record CouponResult(String code, boolean applied, String errorCode, String message) {}
    public record CodOption(boolean eligible, long feePaise, Long totalPaise, List<String> reasons) {}
    public record Computed(CartType cartType, String pincode, List<PricingEngine.PricedLine> lines,
                           long subtotalPaise, long discountPaise, long shippingPaise, long taxIncludedPaise,
                           long totalPaise, CouponResult coupon, CodOption cod, String fingerprint) {}

    public Computed compute(String owner, CartType type, String pincode, String couponCode, String phoneForCod) {
        if (!Pincodes.isValid(pincode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PINCODE", "Enter a valid 6-digit PIN code.");
        }
        if (!Pincodes.isServiceable(pincode, props.nonServiceablePincodePrefixes())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PINCODE_NOT_SERVICEABLE",
                    "Sorry, we do not deliver to this PIN code yet.");
        }
        var cart = clients.cart(owner, type);
        if (cart.lines().isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_CART", "Your cart is empty.");
        }
        List<CartLine> lines = cart.lines().stream().sorted(Comparator.comparing(CartLine::sku)).toList();
        List<String> skus = lines.stream().map(CartLine::sku).toList();
        var info = clients.skus(skus);

        List<String> invalid = new ArrayList<>();
        for (CartLine l : lines) {
            var s = info.get(l.sku());
            boolean bad = s == null || !s.active()
                    || (type == CartType.BULK && (!s.bulkEligible() || s.bulkUnitPricePaise() == null))
                    || l.quantity() > (type == CartType.BULK ? BULK_MAX_LINE_QTY : s.maxPerOrder());
            if (bad) invalid.add(l.sku());
        }
        if (!invalid.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CART_INVALID",
                    "Some items in your cart need attention before checkout.", Map.of("skus", invalid));
        }
        if (type == CartType.BULK) enforceBulkMinimum(lines);

        Map<String, Integer> stock = clients.availability(skus);
        List<String> short_ = lines.stream().filter(l -> stock.getOrDefault(l.sku(), 0) < l.quantity())
                .map(CartLine::sku).toList();
        if (!short_.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Some items no longer have enough stock.",
                    Map.of("skus", short_));
        }

        List<PricingEngine.Input> inputs = lines.stream().map(l -> {
            var s = info.get(l.sku());
            long unit = type == CartType.BULK ? s.bulkUnitPricePaise() : s.unitPricePaise();
            return new PricingEngine.Input(l.sku(), s.productName(), s.colorName(), unit, l.quantity());
        }).toList();
        long subtotal = inputs.stream().mapToLong(i -> Math.multiplyExact(i.unitPricePaise(), (long) i.quantity())).sum();

        CouponResult couponResult = null;
        long discount = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            String code = couponCode.strip().toUpperCase();
            var coupon = code.matches("^[A-Z0-9]{3,20}$") ? repo.coupon(code) : java.util.Optional.<Coupon>empty();
            if (coupon.isEmpty()) {
                couponResult = new CouponResult(code, false, "INVALID_COUPON", "This coupon code is not valid.");
            } else {
                var eval = coupon.get().evaluate(subtotal, type, clock.instant());
                couponResult = new CouponResult(code, eval.applied(), eval.errorCode(), eval.message());
                discount = eval.discountPaise();
            }
        }

        var totals = engine.price(type, inputs, discount);
        CodOption cod = codOption(owner, pincode, totals.totalPaise(), type, phoneForCod);
        return new Computed(type, pincode, totals.lines(), totals.subtotalPaise(), totals.discountPaise(),
                totals.shippingPaise(), totals.taxIncludedPaise(), totals.totalPaise(), couponResult, cod,
                fingerprint(type, lines));
    }

    private void enforceBulkMinimum(List<CartLine> lines) {
        int units = lines.stream().mapToInt(CartLine::quantity).sum();
        boolean met = props.bulkAllowMixedSkus() ? units >= props.bulkMinimumUnits()
                : lines.stream().allMatch(l -> l.quantity() >= props.bulkMinimumUnits());
        if (!met) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BULK_MINIMUM_NOT_MET",
                    props.bulkAllowMixedSkus()
                            ? "Bulk orders need at least " + props.bulkMinimumUnits() + " pieces in total."
                            : "Bulk orders need at least " + props.bulkMinimumUnits() + " pieces of each style.",
                    Map.of("bulk", Map.of("minimumUnits", props.bulkMinimumUnits(), "units", units)));
        }
    }

    private CodOption codOption(String owner, String pincode, long total, CartType type, String phone) {
        try {
            var e = clients.codEligibility(owner, pincode, total, type, phone);
            return new CodOption(e.eligible(), e.eligible() ? e.feePaise() : 0,
                    e.eligible() ? Math.addExact(total, e.feePaise()) : null, e.reasons());
        } catch (UpstreamException | ApiException ex) {
            log.warn("COD eligibility unavailable: {}", ex.getMessage());
            return new CodOption(false, 0, null, List.of("COD_TEMPORARILY_UNAVAILABLE"));
        }
    }

    static String fingerprint(CartType type, List<CartLine> lines) {
        String canonical = type + "|" + lines.stream().sorted(Comparator.comparing(CartLine::sku))
                .map(l -> l.sku() + ":" + l.quantity()).collect(Collectors.joining(","));
        return sha256(canonical);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
