package com.aurelia.payment.application;

import com.aurelia.payment.config.RazorpayProperties;
import com.aurelia.payment.domain.Signatures;
import com.aurelia.payment.infrastructure.OrderClient;
import com.aurelia.payment.infrastructure.PaymentRepository;
import com.aurelia.payment.infrastructure.PaymentRepository.ProviderOrderRow;
import com.aurelia.payment.infrastructure.RazorpayClient;
import com.aurelia.platform.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Razorpay Test Mode orchestration. An order is only confirmed from trusted evidence:
 * a server-verified checkout signature followed by an authoritative payment fetch, or a webhook whose
 * HMAC over the raw body verifies with the separate webhook secret.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final RazorpayProperties props;
    private final RazorpayClient razorpay;
    private final OrderClient orders;
    private final PaymentRepository repo;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public PaymentService(RazorpayProperties props, RazorpayClient razorpay, OrderClient orders, PaymentRepository repo,
                          TransactionTemplate tx, ObjectMapper mapper) {
        this.props = props;
        this.razorpay = razorpay;
        this.orders = orders;
        this.repo = repo;
        this.tx = tx;
        this.mapper = mapper;
    }

    public record Prefill(String name, String email, String contact) {}
    public record CheckoutOptions(String keyId, String razorpayOrderId, long amountPaise, String currency,
                                  String orderNumber, String merchantName, String description, Prefill prefill,
                                  boolean testMode) {}
    public record VerifyResult(String paymentStatus, String orderStatus, String orderNumber) {}

    public boolean enabled() {
        return props.enabled();
    }

    public CheckoutOptions createProviderOrder(String owner, UUID orderId) {
        requireEnabled();
        OrderClient.InternalOrder order = ownedOrder(owner, orderId);
        if (!"ONLINE".equals(order.paymentMethod())) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ONLINE_ORDER", "This order uses cash on delivery.");
        }
        if (!"PENDING_PAYMENT".equals(order.status())
                || (order.expiresAt() != null && order.expiresAt().isBefore(Instant.now()))) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "This order can no longer be paid.",
                    java.util.Map.of("status", order.status()));
        }
        ProviderOrderRow row = tx.execute(s -> {
            repo.lockOrder(orderId);
            var open = repo.openFor(orderId);
            if (open.isPresent() && open.get().amountPaise() == order.totalPaise() && open.get().currency().equals(order.currency())) {
                return open.get(); // reuse: one Razorpay order can take several attempts
            }
            open.ifPresent(o -> repo.voidProviderOrder(o.razorpayOrderId()));
            var created = razorpay.createOrder(order.totalPaise(), order.currency(), order.orderNumber(), orderId.toString());
            if (created == null || created.id() == null || created.amount() != order.totalPaise()
                    || !order.currency().equals(created.currency())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_ORDER_MISMATCH", "Payment provider returned an unexpected order.");
            }
            repo.insertProviderOrder(orderId, created.id(), created.amount(), created.currency());
            return new ProviderOrderRow(orderId, created.id(), created.amount(), created.currency(), "CREATED");
        });
        return new CheckoutOptions(props.keyId(), row.razorpayOrderId(), row.amountPaise(), row.currency(),
                order.orderNumber(), props.merchantName(), "Order " + order.orderNumber() + " — TEST PAYMENT, no real money",
                new Prefill(order.contactName(), order.contactEmail(), order.contactPhone()), true);
    }

    public VerifyResult verifyCheckout(String owner, UUID orderId, String rzpOrderId, String rzpPaymentId, String signature) {
        requireEnabled();
        OrderClient.InternalOrder order = ownedOrder(owner, orderId);
        ProviderOrderRow po = repo.byRazorpayOrderId(rzpOrderId).filter(p -> p.orderId().equals(orderId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found for this order."));
        if (!Signatures.verifyPayment(rzpOrderId, rzpPaymentId, signature, props.keySecret())) {
            repo.attempt(orderId, rzpOrderId, null, "SIGNATURE_INVALID", "CHECKOUT", null, null, null, null);
            log.warn("Invalid Razorpay checkout signature for order {}", order.orderNumber());
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Payment could not be verified.");
        }
        // Authoritative status from the provider — the browser callback alone is never trusted.
        var payment = razorpay.fetchPayment(rzpPaymentId);
        if (payment == null || !rzpOrderId.equals(payment.order_id()) || payment.amount() != po.amountPaise()
                || !po.currency().equals(payment.currency())) {
            repo.attempt(orderId, rzpOrderId, rzpPaymentId, "MISMATCH", "CHECKOUT", null, "MISMATCH",
                    "Provider payment does not match the order", payment == null ? null : payment.amount());
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_MISMATCH", "Payment details do not match this order.");
        }
        return switch (payment.status()) {
            case "captured" -> finalizeCapture(po, rzpPaymentId, payment.amount(), payment.currency(), payment.method(), "CHECKOUT");
            case "failed" -> {
                repo.attempt(orderId, rzpOrderId, rzpPaymentId, "FAILED", "CHECKOUT", payment.method(),
                        payment.error_code(), payment.error_description(), payment.amount());
                yield new VerifyResult("FAILED", order.status(), order.orderNumber());
            }
            default -> { // created / authorized: capture is completed by Razorpay; webhook will confirm.
                repo.attempt(orderId, rzpOrderId, rzpPaymentId, payment.status().toUpperCase(), "CHECKOUT",
                        payment.method(), null, null, payment.amount());
                yield new VerifyResult("PENDING", order.status(), order.orderNumber());
            }
        };
    }

    /** Client-reported failure/cancel: informational only, never changes payment truth. */
    public void reportClientFailure(String owner, UUID orderId, String rzpOrderId, String code, String description) {
        ownedOrder(owner, orderId);
        repo.byRazorpayOrderId(rzpOrderId).filter(p -> p.orderId().equals(orderId)).ifPresent(p ->
                repo.attempt(orderId, rzpOrderId, null, "CLIENT_REPORTED_FAILURE", "CLIENT_REPORT", null, code, description, null));
    }

    public enum WebhookOutcome { PROCESSED, DUPLICATE, IGNORED }

    public WebhookOutcome handleWebhook(byte[] rawBody, String signature, String eventIdHeader) {
        requireEnabled();
        if (!Signatures.verifyWebhook(rawBody, signature, props.webhookSecret())) {
            log.warn("Rejected Razorpay webhook with invalid signature");
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Invalid webhook signature.");
        }
        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed webhook payload.");
        }
        String event = root.path("event").asText("");
        String sha = Signatures.sha256Hex(rawBody);
        String eventId = eventIdHeader != null && eventIdHeader.matches("^[A-Za-z0-9_-]{1,80}$") ? eventIdHeader : "sha-" + sha.substring(0, 60);
        if (!repo.recordWebhook(eventId, event, sha)) return WebhookOutcome.DUPLICATE;
        try {
            WebhookOutcome outcome = process(event, root.path("payload"));
            repo.completeWebhook(eventId, outcome.name());
            return outcome;
        } catch (RuntimeException e) {
            repo.forgetWebhook(eventId); // allow Razorpay's retry to be processed
            throw e;
        }
    }

    private WebhookOutcome process(String event, JsonNode payload) {
        JsonNode p = payload.path("payment").path("entity");
        String rzpOrderId = p.path("order_id").asText(null);
        String paymentId = p.path("id").asText(null);
        if (rzpOrderId == null || paymentId == null) return WebhookOutcome.IGNORED;
        var po = repo.byRazorpayOrderId(rzpOrderId);
        if (po.isEmpty()) {
            log.info("Webhook {} for unknown provider order; ignoring", event);
            return WebhookOutcome.IGNORED;
        }
        long amount = p.path("amount").asLong(-1);
        String currency = p.path("currency").asText("");
        switch (event) {
            case "payment.captured", "order.paid" -> {
                if (!"captured".equals(p.path("status").asText())) return WebhookOutcome.IGNORED;
                if (amount != po.get().amountPaise() || !po.get().currency().equals(currency)) {
                    repo.attempt(po.get().orderId(), rzpOrderId, paymentId, "MISMATCH", "WEBHOOK", null, "MISMATCH",
                            "Webhook amount/currency mismatch", amount);
                    log.error("Webhook amount mismatch for provider order {}", rzpOrderId);
                    return WebhookOutcome.IGNORED;
                }
                finalizeCapture(po.get(), paymentId, amount, currency, p.path("method").asText(null), "WEBHOOK");
                return WebhookOutcome.PROCESSED;
            }
            case "payment.failed" -> {
                repo.attempt(po.get().orderId(), rzpOrderId, paymentId, "FAILED", "WEBHOOK", p.path("method").asText(null),
                        p.path("error_code").asText(null), p.path("error_description").asText(null), amount);
                return WebhookOutcome.PROCESSED;
            }
            default -> {
                return WebhookOutcome.IGNORED;
            }
        }
    }

    private VerifyResult finalizeCapture(ProviderOrderRow po, String paymentId, long amount, String currency,
                                         String method, String source) {
        repo.attempt(po.orderId(), po.razorpayOrderId(), paymentId, "CAPTURED", source, method, null, null, amount);
        repo.markPaid(po.razorpayOrderId());
        var ref = orders.paymentCaptured(po.orderId(), paymentId, amount, currency);
        log.info("Payment captured (test mode) for order {} -> {}", ref.orderNumber(), ref.status());
        return new VerifyResult("CAPTURED", ref.status(), ref.orderNumber());
    }

    private OrderClient.InternalOrder ownedOrder(String owner, UUID orderId) {
        OrderClient.InternalOrder order;
        try {
            order = orders.get(orderId);
        } catch (ApiException e) {
            throw notFound();
        }
        if (!MessageDigest.isEqual(order.ownerHash().getBytes(StandardCharsets.UTF_8), owner.getBytes(StandardCharsets.UTF_8))) {
            throw notFound();
        }
        return order;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found.");
    }

    private void requireEnabled() {
        if (!props.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ONLINE_PAYMENTS_DISABLED",
                    "Online payments are not configured. Please choose cash on delivery.");
        }
    }
}
