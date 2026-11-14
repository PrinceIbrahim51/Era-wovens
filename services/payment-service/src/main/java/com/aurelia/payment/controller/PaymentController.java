package com.aurelia.payment.controller;

import com.aurelia.payment.application.PaymentService;
import com.aurelia.platform.security.GuestSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
    private static final String RZP_ORDER = "^order_[A-Za-z0-9]{6,40}$";
    private static final String RZP_PAYMENT = "^pay_[A-Za-z0-9]{6,40}$";
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    public record CreateRequest(@NotNull UUID orderId) {}
    public record VerifyRequest(@NotNull UUID orderId, @NotNull @Pattern(regexp = RZP_ORDER) String razorpayOrderId,
                                @NotNull @Pattern(regexp = RZP_PAYMENT) String razorpayPaymentId,
                                @NotNull @Pattern(regexp = "^[0-9a-f]{64}$") String razorpaySignature) {}
    public record FailureReport(@NotNull UUID orderId, @NotNull @Pattern(regexp = RZP_ORDER) String razorpayOrderId,
                                @Size(max = 60) String code, @Size(max = 300) String description) {}

    @GetMapping("/api/v1/payments/config")
    public Map<String, Object> config() {
        return Map.of("onlineEnabled", service.enabled(), "mode", "test");
    }

    @PostMapping("/api/v1/payments/razorpay/orders")
    public PaymentService.CheckoutOptions create(HttpServletRequest req, @Valid @RequestBody CreateRequest body) {
        return service.createProviderOrder(GuestSession.require(req), body.orderId());
    }

    @PostMapping("/api/v1/payments/razorpay/verify")
    public PaymentService.VerifyResult verify(HttpServletRequest req, @Valid @RequestBody VerifyRequest b) {
        return service.verifyCheckout(GuestSession.require(req), b.orderId(), b.razorpayOrderId(), b.razorpayPaymentId(),
                b.razorpaySignature());
    }

    @PostMapping("/api/v1/payments/razorpay/failure")
    public ResponseEntity<Void> failure(HttpServletRequest req, @Valid @RequestBody FailureReport b) {
        service.reportClientFailure(GuestSession.require(req), b.orderId(), b.razorpayOrderId(), b.code(), b.description());
        return ResponseEntity.accepted().build();
    }

    /** Signature is computed over the exact raw bytes, so the body is bound as byte[] (never re-serialised). */
    @PostMapping(value = "/api/v1/webhooks/razorpay", consumes = "application/json")
    public Map<String, String> webhook(@RequestBody byte[] rawBody,
                                       @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                       @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        return Map.of("outcome", service.handleWebhook(rawBody, signature, eventId).name());
    }
}
