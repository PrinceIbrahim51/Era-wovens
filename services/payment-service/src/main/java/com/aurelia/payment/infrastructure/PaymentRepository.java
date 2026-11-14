package com.aurelia.payment.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ProviderOrderRow(UUID orderId, String razorpayOrderId, long amountPaise, String currency, String status) {}

    public void lockOrder(UUID orderId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:k, 0))").param("k", orderId.toString())
                .query(Object.class).optional();
    }

    public Optional<ProviderOrderRow> openFor(UUID orderId) {
        return jdbc.sql("SELECT order_id, razorpay_order_id, amount_paise, currency, status FROM provider_orders WHERE order_id = :o AND status = 'CREATED'")
                .param("o", orderId).query(this::map).optional();
    }

    public Optional<ProviderOrderRow> byRazorpayOrderId(String rzpOrderId) {
        return jdbc.sql("SELECT order_id, razorpay_order_id, amount_paise, currency, status FROM provider_orders WHERE razorpay_order_id = :r")
                .param("r", rzpOrderId).query(this::map).optional();
    }

    public void insertProviderOrder(UUID orderId, String rzpOrderId, long amount, String currency) {
        jdbc.sql("INSERT INTO provider_orders (order_id, razorpay_order_id, amount_paise, currency, status) VALUES (:o, :r, :a, :c, 'CREATED')")
                .param("o", orderId).param("r", rzpOrderId).param("a", amount).param("c", currency).update();
    }

    public void voidProviderOrder(String rzpOrderId) {
        jdbc.sql("UPDATE provider_orders SET status = 'FAILED', updated_at = now() WHERE razorpay_order_id = :r AND status = 'CREATED'")
                .param("r", rzpOrderId).update();
    }

    public void markPaid(String rzpOrderId) {
        jdbc.sql("UPDATE provider_orders SET status = 'PAID', updated_at = now() WHERE razorpay_order_id = :r")
                .param("r", rzpOrderId).update();
    }

    public void attempt(UUID orderId, String rzpOrderId, String paymentId, String status, String source, String method,
                        String errorCode, String errorDescription, Long amount) {
        jdbc.sql("""
                INSERT INTO payment_attempts (order_id, razorpay_order_id, razorpay_payment_id, status, source, method,
                    error_code, error_description, amount_paise)
                VALUES (:o, :r, :p, :s, :src, :m, :ec, :ed, :a)
                ON CONFLICT DO NOTHING
                """).param("o", orderId).param("r", rzpOrderId).param("p", paymentId).param("s", status)
                .param("src", source).param("m", method).param("ec", truncate(errorCode, 60))
                .param("ed", truncate(errorDescription, 300)).param("a", amount).update();
    }

    /** @return true if this event id is new (first delivery). */
    public boolean recordWebhook(String eventId, String type, String sha) {
        return jdbc.sql("INSERT INTO webhook_receipts (event_id, event_type, payload_sha256) VALUES (:id, :t, :h) ON CONFLICT DO NOTHING")
                .param("id", eventId).param("t", type).param("h", sha).update() == 1;
    }

    public void completeWebhook(String eventId, String outcome) {
        jdbc.sql("UPDATE webhook_receipts SET outcome = :o, processed_at = now() WHERE event_id = :id")
                .param("o", outcome).param("id", eventId).update();
    }

    public void forgetWebhook(String eventId) {
        jdbc.sql("DELETE FROM webhook_receipts WHERE event_id = :id AND processed_at IS NULL").param("id", eventId).update();
    }

    private ProviderOrderRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new ProviderOrderRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5));
    }

    private static String truncate(String s, int n) {
        return s == null ? null : s.substring(0, Math.min(n, s.length()));
    }
}
