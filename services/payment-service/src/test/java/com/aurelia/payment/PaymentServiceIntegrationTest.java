package com.aurelia.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurelia.payment.application.PaymentService;
import com.aurelia.payment.domain.Signatures;
import com.aurelia.payment.infrastructure.OrderClient;
import com.aurelia.platform.web.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the payment flow against a local fake of the Razorpay REST API (no network, no real money). */
@SpringBootTest(properties = {
        "platform.security.gateway-token=test-gateway-token-0123456789abcdef",
        "platform.security.service-token=test-service-token-0123456789abcdef",
        "payments.razorpay.enabled=true", "payments.razorpay.mode=test",
        "payments.razorpay.key-id=rzp_test_FAKEKEY1234",
        "payments.razorpay.key-secret=fake_key_secret_0123456789",
        "payments.razorpay.webhook-secret=fake_webhook_secret_0123456789"})
@Testcontainers(disabledWithoutDocker = true)
class PaymentServiceIntegrationTest {
    static final String KEY_SECRET = "fake_key_secret_0123456789";
    static final String WEBHOOK_SECRET = "fake_webhook_secret_0123456789";
    static final String OWNER = "d".repeat(64);
    static final AtomicReference<String> paymentJson = new AtomicReference<>();
    static final AtomicReference<String> nextOrderId = new AtomicReference<>("order_FAKE000001");
    static HttpServer fake;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void razorpay(DynamicPropertyRegistry r) throws IOException {
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/v1/orders", ex -> {
            String req = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            long amount = Long.parseLong(req.replaceAll(".*\"amount\":(\\d+).*", "$1"));
            respond(ex, "{\"id\":\"" + nextOrderId.get() + "\",\"amount\":" + amount + ",\"currency\":\"INR\",\"status\":\"created\",\"receipt\":\"r\"}");
        });
        fake.createContext("/v1/payments/", ex -> respond(ex, paymentJson.get()));
        fake.start();
        r.add("payments.razorpay.api-base-url", () -> "http://127.0.0.1:" + fake.getAddress().getPort());
    }

    static void respond(com.sun.net.httpserver.HttpExchange ex, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @AfterAll
    static void stop() {
        fake.stop(0);
    }

    @Autowired PaymentService service;
    @MockitoBean OrderClient orders;
    UUID orderId;
    String rzpOrder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        rzpOrder = "order_FAKE" + orderId.toString().substring(0, 8).replace("-", "");
        nextOrderId.set(rzpOrder);
        when(orders.get(orderId)).thenReturn(new OrderClient.InternalOrder(orderId, "ERA-261114-ABCDEF", OWNER,
                "PENDING_PAYMENT", "ONLINE", "PENDING", 154_900, "INR", "Asha", "asha@example.com", "9876543210",
                Instant.now().plusSeconds(600)));
        when(orders.paymentCaptured(eq(orderId), anyString(), anyLong(), anyString()))
                .thenReturn(new OrderClient.OrderRef(orderId, "ERA-261114-ABCDEF", "CONFIRMED"));
    }

    @Test
    void createReturnsPublicKeyOnlyAndReusesOpenOrder() {
        var opts = service.createProviderOrder(OWNER, orderId);
        assertThat(opts.keyId()).startsWith("rzp_test_");
        assertThat(opts.testMode()).isTrue();
        assertThat(opts.toString()).doesNotContain(KEY_SECRET);
        assertThat(service.createProviderOrder(OWNER, orderId).razorpayOrderId()).isEqualTo(opts.razorpayOrderId());
    }

    @Test
    void otherOwnersCannotPay() {
        assertThatThrownBy(() -> service.createProviderOrder("e".repeat(64), orderId))
                .isInstanceOf(ApiException.class).hasMessageContaining("not found");
    }

    @Test
    void verifiedCapturedPaymentConfirmsOrder() {
        service.createProviderOrder(OWNER, orderId);
        String pay = "pay_OK" + orderId.toString().substring(0, 8);
        paymentJson.set("{\"id\":\"" + pay + "\",\"order_id\":\"" + rzpOrder + "\",\"amount\":154900,\"currency\":\"INR\",\"status\":\"captured\",\"method\":\"upi\"}");
        String sig = Signatures.hmacHexString((rzpOrder + "|" + pay).getBytes(StandardCharsets.UTF_8), KEY_SECRET);
        var res = service.verifyCheckout(OWNER, orderId, rzpOrder, pay, sig);
        assertThat(res.paymentStatus()).isEqualTo("CAPTURED");
        assertThat(res.orderStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void forgedSignatureAndTamperedAmountAreRejected() {
        service.createProviderOrder(OWNER, orderId);
        String pay = "pay_BAD" + orderId.toString().substring(0, 8);
        assertThatThrownBy(() -> service.verifyCheckout(OWNER, orderId, rzpOrder, pay, "0".repeat(64)))
                .hasMessageContaining("could not be verified");

        paymentJson.set("{\"id\":\"" + pay + "\",\"order_id\":\"" + rzpOrder + "\",\"amount\":100,\"currency\":\"INR\",\"status\":\"captured\"}");
        String sig = Signatures.hmacHexString((rzpOrder + "|" + pay).getBytes(StandardCharsets.UTF_8), KEY_SECRET);
        assertThatThrownBy(() -> service.verifyCheckout(OWNER, orderId, rzpOrder, pay, sig)).hasMessageContaining("do not match");
        verify(orders, never()).paymentCaptured(any(), anyString(), anyLong(), anyString());
    }

    @Test
    void webhookIsVerifiedAndDeduplicated() {
        service.createProviderOrder(OWNER, orderId);
        String pay = "pay_WH" + orderId.toString().substring(0, 8);
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + pay
                + "\",\"order_id\":\"" + rzpOrder + "\",\"amount\":154900,\"currency\":\"INR\",\"status\":\"captured\"}}}}";
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        String sig = Signatures.hmacHexString(raw, WEBHOOK_SECRET);
        String eventId = "evt_" + orderId.toString().substring(0, 8);

        assertThatThrownBy(() -> service.handleWebhook(raw, Signatures.hmacHexString(raw, KEY_SECRET), eventId))
                .hasMessageContaining("Invalid webhook signature"); // signed with the wrong secret
        assertThat(service.handleWebhook(raw, sig, eventId)).isEqualTo(PaymentService.WebhookOutcome.PROCESSED);
        assertThat(service.handleWebhook(raw, sig, eventId)).isEqualTo(PaymentService.WebhookOutcome.DUPLICATE);
        verify(orders, times(1)).paymentCaptured(eq(orderId), eq(pay), eq(154_900L), eq("INR"));
    }

    @Test
    void webhookWithWrongAmountDoesNotConfirm() {
        service.createProviderOrder(OWNER, orderId);
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_AMT12345678\",\"order_id\":\""
                + rzpOrder + "\",\"amount\":1,\"currency\":\"INR\",\"status\":\"captured\"}}}}";
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        assertThat(service.handleWebhook(raw, Signatures.hmacHexString(raw, WEBHOOK_SECRET), "evt_amt_" + orderId.toString().substring(0, 6)))
                .isEqualTo(PaymentService.WebhookOutcome.IGNORED);
        verify(orders, never()).paymentCaptured(any(), anyString(), anyLong(), anyString());
    }
}
