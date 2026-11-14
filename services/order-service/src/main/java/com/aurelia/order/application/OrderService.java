package com.aurelia.order.application;

import com.aurelia.order.config.OrderProperties;
import com.aurelia.order.domain.OrderStatus;
import com.aurelia.order.domain.PaymentStatus;
import com.aurelia.order.infrastructure.InventoryClient;
import com.aurelia.order.infrastructure.OrderRepository;
import com.aurelia.order.infrastructure.OrderRepository.ItemRow;
import com.aurelia.order.infrastructure.OrderRepository.OrderRow;
import com.aurelia.platform.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("Asia/Kolkata"));
    private static final Set<OrderStatus> PUBLISHED = Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED,
            OrderStatus.EXPIRED, OrderStatus.REVIEW_REQUIRED);

    private final OrderRepository repo;
    private final InventoryClient inventory;
    private final OrderProperties props;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public OrderService(OrderRepository repo, InventoryClient inventory, OrderProperties props, TransactionTemplate tx,
                        ObjectMapper mapper) {
        this.repo = repo;
        this.inventory = inventory;
        this.props = props;
        this.tx = tx;
        this.mapper = mapper;
    }

    public record Contact(String fullName, String email, String phone) {}
    public record Address(String line1, String line2, String landmark, String city, String state, String pincode) {}
    public record Line(String sku, String productName, String colorName, long unitPricePaise, int quantity,
                       long lineTotalPaise, long discountPaise, int taxRateBp, long taxIncludedPaise) {}
    public record CreateOrder(String idempotencyKey, String ownerHash, String cartType, String paymentMethod,
                              Contact contact, Address shippingAddress, List<Line> lines, long subtotalPaise,
                              long discountPaise, long shippingPaise, long codFeePaise, long taxIncludedPaise,
                              long totalPaise, String currency, String couponCode) {}

    /** Idempotent on idempotencyKey; validates snapshot arithmetic before persisting. */
    public OrderRow create(CreateOrder c) {
        var existing = repo.byIdempotencyKey(c.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        validateSnapshot(c);
        for (int attempt = 0; attempt < 5; attempt++) {
            UUID id = UUID.randomUUID();
            String number = "AUR-" + DAY.format(Instant.now()) + "-" + randomCode(6);
            if (repo.numberExists(number)) continue;
            var row = new OrderRow(id, number, c.ownerHash(), c.cartType(), c.paymentMethod(), OrderStatus.DRAFT,
                    PaymentStatus.NOT_STARTED, c.contact().fullName(), c.contact().email(), c.contact().phone(),
                    json(c.shippingAddress()), c.subtotalPaise(), c.discountPaise(), c.shippingPaise(), c.codFeePaise(),
                    c.taxIncludedPaise(), c.totalPaise(), c.currency(), c.couponCode(), null, 0,
                    Instant.now().plus(props.draftTtl()), Instant.now());
            try {
                tx.executeWithoutResult(s -> {
                    repo.insert(row, c.idempotencyKey());
                    c.lines().forEach(l -> repo.insertItem(id, new ItemRow(l.sku(), l.productName(), l.colorName(),
                            l.unitPricePaise(), l.quantity(), l.lineTotalPaise(), l.discountPaise(), l.taxRateBp(),
                            l.taxIncludedPaise())));
                    repo.event(id, null, OrderStatus.DRAFT, "Order created", "checkout-service");
                });
                return repo.byId(id).orElseThrow();
            } catch (DuplicateKeyException e) {
                var raced = repo.byIdempotencyKey(c.idempotencyKey());
                if (raced.isPresent()) return raced.get();
            }
        }
        throw new IllegalStateException("could not allocate order number");
    }

    private static void validateSnapshot(CreateOrder c) {
        long sub = 0, disc = 0, tax = 0;
        for (Line l : c.lines()) {
            if (l.lineTotalPaise() != Math.multiplyExact(l.unitPricePaise(), (long) l.quantity())) bad("line total");
            sub += l.lineTotalPaise();
            disc += l.discountPaise();
            tax += l.taxIncludedPaise();
        }
        if (sub != c.subtotalPaise() || disc != c.discountPaise() || tax != c.taxIncludedPaise()
                || c.totalPaise() != c.subtotalPaise() - c.discountPaise() + c.shippingPaise() + c.codFeePaise()
                || !"INR".equals(c.currency())) {
            bad("totals");
        }
        if ("ONLINE".equals(c.paymentMethod()) && c.codFeePaise() != 0) bad("cod fee on online order");
    }

    private static void bad(String what) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SNAPSHOT", "Order snapshot is inconsistent: " + what);
    }

    /** Applies a legal transition with its side effects. Idempotent when already in the target state. */
    public OrderRow transition(UUID id, OrderStatus to, String reason, String actor) {
        for (int attempt = 0; attempt < 3; attempt++) {
            OrderRow o = repo.byId(id).orElseThrow(OrderService::notFound);
            if (o.status() == to) return o;
            if (!o.status().canTransitionTo(to)) {
                throw new ApiException(HttpStatus.CONFLICT, "ILLEGAL_TRANSITION",
                        "Order cannot move from " + o.status() + " to " + to + ".", Map.of("status", o.status().name()));
            }
            OrderStatus target = to;
            String why = reason;
            if (to == OrderStatus.CONFIRMED && o.status().holdsReservation()) {
                try {
                    inventory.commit(id);
                } catch (ApiException e) {
                    log.warn("Stock commit failed for {}: {}", o.orderNumber(), e.code());
                    target = OrderStatus.REVIEW_REQUIRED;
                    why = "Stock could not be committed (" + e.code() + ")";
                }
            }
            if (apply(o, target, paymentStatusFor(o, target, null), why, actor, null)) {
                if ((target == OrderStatus.CANCELLED || target == OrderStatus.EXPIRED) && o.status().holdsReservation()) {
                    releaseQuietly(id);
                }
                return repo.byId(id).orElseThrow();
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Order was updated concurrently; retry.");
    }

    /** Called by payment-service only after verified, captured payment evidence. */
    public OrderRow paymentCaptured(UUID id, String providerPaymentId, long amountPaise, String currency) {
        for (int attempt = 0; attempt < 3; attempt++) {
            OrderRow o = repo.byId(id).orElseThrow(OrderService::notFound);
            if (!"ONLINE".equals(o.paymentMethod())) {
                throw new ApiException(HttpStatus.CONFLICT, "NOT_ONLINE_ORDER", "Order is not an online-payment order.");
            }
            if (o.totalPaise() != amountPaise || !o.currency().equals(currency)) {
                throw new ApiException(HttpStatus.CONFLICT, "AMOUNT_MISMATCH", "Captured amount does not match the order.");
            }
            if (providerPaymentId.equals(o.providerPaymentId())) return o; // replay
            if (o.providerPaymentId() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID", "Order already has a different captured payment.");
            }
            boolean ok = switch (o.status()) {
                case PENDING_PAYMENT, DRAFT -> {
                    OrderStatus target = OrderStatus.CONFIRMED;
                    String why = "Online payment captured";
                    try {
                        inventory.commit(id);
                    } catch (ApiException e) {
                        target = OrderStatus.REVIEW_REQUIRED;
                        why = "Paid, but stock could not be committed (" + e.code() + ")";
                    }
                    if (o.status() == OrderStatus.DRAFT) target = OrderStatus.REVIEW_REQUIRED;
                    yield apply(o, target, PaymentStatus.PAID, why, "payment-service", providerPaymentId);
                }
                case EXPIRED -> apply(o, OrderStatus.REVIEW_REQUIRED, PaymentStatus.PAID,
                        "Payment captured after the payment window closed", "payment-service", providerPaymentId);
                case CANCELLED -> apply(o, OrderStatus.CANCELLED, PaymentStatus.REFUND_PENDING,
                        "Payment captured on a cancelled order; refund required", "payment-service", providerPaymentId);
                default -> apply(o, OrderStatus.REVIEW_REQUIRED, PaymentStatus.PAID,
                        "Unexpected capture in status " + o.status(), "payment-service", providerPaymentId);
            };
            if (ok) return repo.byId(id).orElseThrow();
        }
        throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Order was updated concurrently; retry.");
    }

    public void expireDue() {
        for (UUID id : repo.dueForExpiry(100)) {
            try {
                transition(id, OrderStatus.EXPIRED, "Payment window elapsed", "order-expiry-job");
            } catch (ApiException e) {
                log.debug("Skip expiry for {}: {}", id, e.code());
            }
        }
    }

    private boolean apply(OrderRow o, OrderStatus to, PaymentStatus ps, String reason, String actor, String providerPaymentId) {
        Instant expires = to == OrderStatus.PENDING_PAYMENT ? Instant.now().plus(props.paymentWindow()) : null;
        Boolean ok = tx.execute(s -> {
            if (!repo.updateStatus(o.id(), o.version(), o.status(), to, ps, expires, providerPaymentId)) return false;
            if (to != o.status()) repo.event(o.id(), o.status(), to, reason, actor);
            else repo.event(o.id(), o.status(), to, reason + " (payment status " + ps + ")", actor);
            if (PUBLISHED.contains(to) && to != o.status()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("orderId", o.id());
                payload.put("orderNumber", o.orderNumber());
                payload.put("status", to.name());
                payload.put("paymentMethod", o.paymentMethod());
                payload.put("totalPaise", o.totalPaise());
                payload.put("currency", o.currency());
                payload.put("contactName", o.contactName());
                payload.put("contactEmail", o.contactEmail());
                payload.put("occurredAt", Instant.now().toString());
                repo.outbox(o.id(), "order." + to.name().toLowerCase(), json(payload));
            }
            return true;
        });
        return Boolean.TRUE.equals(ok);
    }

    private static PaymentStatus paymentStatusFor(OrderRow o, OrderStatus to, String ignored) {
        return switch (to) {
            case PENDING_PAYMENT -> PaymentStatus.PENDING;
            case COD_CONFIRMED -> PaymentStatus.PENDING_COLLECTION;
            case CANCELLED, EXPIRED -> o.paymentStatus() == PaymentStatus.PAID ? PaymentStatus.REFUND_PENDING
                    : o.paymentStatus() == PaymentStatus.COLLECTED ? PaymentStatus.REFUND_PENDING : PaymentStatus.VOID;
            default -> o.paymentStatus();
        };
    }

    private void releaseQuietly(UUID id) {
        try {
            inventory.release(id);
        } catch (RuntimeException e) {
            log.warn("Release failed for {} (reservation TTL will reclaim): {}", id, e.getMessage());
        }
    }

    public OrderRow get(UUID id) {
        return repo.byId(id).orElseThrow(OrderService::notFound);
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found.");
    }

    private String randomCode(int n) {
        char[] out = new char[n];
        for (int i = 0; i < n; i++) out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return new String(out);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
