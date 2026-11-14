package com.aurelia.checkout.application;

import com.aurelia.checkout.application.QuoteCalculator.Computed;
import com.aurelia.checkout.config.CheckoutProperties;
import com.aurelia.checkout.domain.CartType;
import com.aurelia.checkout.domain.PaymentMethod;
import com.aurelia.checkout.domain.PricingEngine;
import com.aurelia.checkout.infrastructure.CheckoutRepository;
import com.aurelia.checkout.infrastructure.CheckoutRepository.StoredQuote;
import com.aurelia.checkout.infrastructure.ServiceClients;
import com.aurelia.checkout.infrastructure.ServiceClients.CartLine;
import com.aurelia.platform.web.ApiException;
import com.aurelia.platform.web.Pii;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {
    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final QuoteCalculator calculator;
    private final CheckoutRepository repo;
    private final ServiceClients clients;
    private final CheckoutProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CheckoutService(QuoteCalculator calculator, CheckoutRepository repo, ServiceClients clients,
                           CheckoutProperties props, ObjectMapper mapper, Clock clock) {
        this.calculator = calculator;
        this.repo = repo;
        this.clients = clients;
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
    }

    public record Quote(UUID quoteId, Instant expiresAt, CartType cartType, String pincode, String currency,
                        List<PricingEngine.PricedLine> lines, long subtotalPaise, long discountPaise,
                        long shippingPaise, long taxIncludedPaise, long totalPaise,
                        QuoteCalculator.CouponResult coupon, QuoteCalculator.CodOption cod, String signature) {}

    public Quote createQuote(String owner, CartType type, String pincode, String coupon) {
        return persist(owner, calculator.compute(owner, type, pincode, coupon, null), coupon);
    }

    private Quote persist(String owner, Computed c, String couponCode) {
        UUID id = UUID.randomUUID();
        Instant expires = clock.instant().plus(props.quoteTtl()).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String sig = sign(id, owner, c, expires);
        Quote q = new Quote(id, expires, c.cartType(), c.pincode(), "INR", c.lines(), c.subtotalPaise(),
                c.discountPaise(), c.shippingPaise(), c.taxIncludedPaise(), c.totalPaise(), c.coupon(), c.cod(), sig);
        repo.saveQuote(new StoredQuote(id, owner, c.cartType().name(), c.pincode(),
                c.coupon() == null ? null : c.coupon().code(), c.fingerprint(), json(q), c.totalPaise(), sig, expires));
        return q;
    }

    // ------------------------------------------------------------------ sessions

    public record Contact(String fullName, String email, String phone) {}
    public record Address(String line1, String line2, String landmark, String city, String state, String pincode) {}
    public record SessionRequest(UUID quoteId, PaymentMethod paymentMethod, Contact contact, Address shippingAddress) {}
    public record SessionResponse(UUID sessionId, UUID orderId, String orderNumber, String status,
                                  PaymentMethod paymentMethod, long totalPaise, String currency) {}
    record StoredError(String code, String detail) {}

    /**
     * Places an order exactly once per (owner, Idempotency-Key). Re-prices immediately before finalisation;
     * any change versus the accepted quote is returned as QUOTE_CHANGED with a fresh quote.
     */
    public SessionResponse placeOrder(String owner, String idempotencyKey, SessionRequest req) {
        String requestHash = QuoteCalculator.sha256(json(req));
        UUID sessionId = UUID.randomUUID();
        if (!repo.startSession(sessionId, owner, idempotencyKey, requestHash, req.quoteId(), req.paymentMethod().name())) {
            return replay(owner, idempotencyKey, requestHash);
        }
        try {
            SessionResponse res = execute(owner, sessionId, req);
            repo.completeSession(sessionId, json(res));
            return res;
        } catch (ApiException e) {
            repo.failSession(sessionId, e.code(), json(new StoredError(e.code(), e.getMessage())));
            throw e;
        } catch (RuntimeException e) {
            repo.failSession(sessionId, "INTERNAL_ERROR", json(new StoredError("CHECKOUT_FAILED", "Checkout failed. Please retry.")));
            throw e;
        }
    }

    private SessionResponse replay(String owner, String key, String requestHash) {
        var s = repo.session(owner, key).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "CHECKOUT_IN_PROGRESS", "Checkout is already in progress."));
        if (!MessageDigest.isEqual(s.requestHash().getBytes(StandardCharsets.UTF_8), requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "This idempotency key was already used for a different request.");
        }
        return switch (s.status()) {
            case "COMPLETED" -> read(s.response(), SessionResponse.class);
            case "FAILED" -> {
                var err = read(s.response(), StoredError.class);
                throw new ApiException(HttpStatus.CONFLICT, err.code(), err.detail());
            }
            default -> throw new ApiException(HttpStatus.CONFLICT, "CHECKOUT_IN_PROGRESS", "Checkout is already in progress.");
        };
    }

    private SessionResponse execute(String owner, UUID sessionId, SessionRequest req) {
        StoredQuote stored = repo.quote(req.quoteId())
                .filter(q -> MessageDigest.isEqual(q.ownerHash().getBytes(StandardCharsets.UTF_8), owner.getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", "Quote not found."));
        Quote accepted = read(stored.payload(), Quote.class);
        if (!verify(stored, accepted)) {
            throw new ApiException(HttpStatus.CONFLICT, "QUOTE_INVALID", "Quote failed verification. Please review your order again.");
        }
        if (clock.instant().isAfter(stored.expiresAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "QUOTE_EXPIRED", "Your quote expired. Please review the updated total.");
        }
        if (!stored.pincode().equals(req.shippingAddress().pincode())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PINCODE_MISMATCH",
                    "The delivery PIN code changed. Please review your total again.");
        }

        // Re-price immediately before finalisation.
        Computed fresh = calculator.compute(owner, accepted.cartType(), stored.pincode(), stored.couponCode(),
                req.contact().phone());
        boolean couponDropped = accepted.coupon() != null && accepted.coupon().applied()
                && (fresh.coupon() == null || !fresh.coupon().applied());
        boolean changed = fresh.totalPaise() != accepted.totalPaise() || !fresh.lines().equals(accepted.lines())
                || !fresh.fingerprint().equals(stored.fingerprint()) || couponDropped;
        if (req.paymentMethod() == PaymentMethod.COD) {
            if (!fresh.cod().eligible()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COD_NOT_ELIGIBLE",
                        "Cash on delivery is not available for this order.", Map.of("reasons", fresh.cod().reasons()));
            }
            changed |= fresh.cod().feePaise() != accepted.cod().feePaise();
        }
        if (changed) {
            Quote newQuote = persist(owner, fresh, stored.couponCode());
            throw new ApiException(HttpStatus.CONFLICT, "QUOTE_CHANGED",
                    "Prices, stock or offers changed. Please review the updated total.", Map.of("quote", newQuote));
        }

        boolean couponRedeemed = false;
        if (fresh.coupon() != null && fresh.coupon().applied()) {
            if (!repo.redeemCoupon(fresh.coupon().code(), sessionId, owner)) {
                throw new ApiException(HttpStatus.CONFLICT, "COUPON_EXHAUSTED", "This coupon is no longer available.");
            }
            couponRedeemed = true;
        }

        long codFee = req.paymentMethod() == PaymentMethod.COD ? fresh.cod().feePaise() : 0;
        long total = Math.addExact(fresh.totalPaise(), codFee);
        var c = req.contact();
        var a = req.shippingAddress();
        List<CartLine> reserveLines = fresh.lines().stream().map(l -> new CartLine(l.sku(), l.quantity())).toList();

        ServiceClients.OrderRef order;
        try {
            order = clients.createOrder(new ServiceClients.CreateOrder(sessionId.toString(), owner, fresh.cartType(),
                    req.paymentMethod(), new ServiceClients.Contact(c.fullName(), c.email(), c.phone()),
                    new ServiceClients.Address(a.line1(), a.line2(), a.landmark(), a.city(), a.state(), a.pincode()),
                    fresh.lines().stream().map(l -> new ServiceClients.OrderLine(l.sku(), l.productName(), l.colorName(),
                            l.unitPricePaise(), l.quantity(), l.lineTotalPaise(), l.discountPaise(), l.taxRateBp(),
                            l.taxIncludedPaise())).toList(),
                    fresh.subtotalPaise(), fresh.discountPaise(), fresh.shippingPaise(), codFee,
                    fresh.taxIncludedPaise(), total, "INR", couponRedeemed ? fresh.coupon().code() : null));
        } catch (RuntimeException e) {
            if (couponRedeemed) repo.unredeemCoupon(sessionId);
            throw e;
        }
        repo.setSessionOrder(sessionId, order.id());

        try {
            clients.reserve(order.id(), reserveLines, props.reservationTtlSeconds());
        } catch (RuntimeException e) {
            compensate(order.id(), sessionId, couponRedeemed, false, "STOCK_RESERVATION_FAILED");
            throw e;
        }

        String status;
        try {
            if (req.paymentMethod() == PaymentMethod.COD) {
                clients.codConfirm(order.id(), owner, a.pincode(), total, codFee, fresh.cartType(), c.phone());
                clients.transition(order.id(), "COD_CONFIRMED", "COD accepted");
                status = clients.transition(order.id(), "CONFIRMED", "COD order confirmed").status();
            } else {
                status = clients.transition(order.id(), "PENDING_PAYMENT", "Awaiting online payment").status();
            }
        } catch (RuntimeException e) {
            compensate(order.id(), sessionId, couponRedeemed, true, "CONFIRMATION_FAILED");
            throw e;
        }

        try {
            clients.consumeCart(owner, fresh.cartType(), reserveLines);
        } catch (RuntimeException e) {
            log.warn("Order {} placed but cart cleanup failed: {}", order.orderNumber(), e.getMessage());
        }
        log.info("Order {} placed via {} for {}", order.orderNumber(), req.paymentMethod(), Pii.maskEmail(c.email()));
        return new SessionResponse(sessionId, order.id(), order.orderNumber(), status, req.paymentMethod(), total, "INR");
    }

    private void compensate(UUID orderId, UUID sessionId, boolean couponRedeemed, boolean releaseStock, String reason) {
        try {
            if (releaseStock) clients.release(orderId);
        } catch (RuntimeException e) {
            log.warn("Release failed for {} (reservation TTL will reclaim it): {}", orderId, e.getMessage());
        }
        try {
            clients.transition(orderId, "CANCELLED", reason);
        } catch (RuntimeException e) {
            log.warn("Cancel failed for {} (order expiry will reclaim it): {}", orderId, e.getMessage());
        }
        if (couponRedeemed) repo.unredeemCoupon(sessionId);
    }

    // ------------------------------------------------------------------ helpers

    private String sign(UUID id, String owner, Computed c, Instant expires) {
        String data = String.join("|", id.toString(), owner, c.cartType().name(), c.pincode(),
                Long.toString(c.totalPaise()), Long.toString(c.cod().feePaise()), Long.toString(expires.getEpochSecond()),
                c.fingerprint());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.quoteSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean verify(StoredQuote stored, Quote q) {
        var c = new Computed(q.cartType(), q.pincode(), q.lines(), q.subtotalPaise(), q.discountPaise(),
                q.shippingPaise(), q.taxIncludedPaise(), q.totalPaise(), q.coupon(), q.cod(), stored.fingerprint());
        String expected = sign(q.quoteId(), stored.ownerHash(), c, stored.expiresAt());
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), stored.signature().getBytes(StandardCharsets.UTF_8));
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T read(String s, Class<T> type) {
        try {
            return mapper.readValue(s, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
