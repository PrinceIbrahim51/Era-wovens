package com.aurelia.checkout.infrastructure;

import com.aurelia.checkout.domain.Coupon;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CheckoutRepository {
    private final JdbcClient jdbc;

    public CheckoutRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- coupons ----------
    public Optional<Coupon> coupon(String code) {
        return jdbc.sql("""
                SELECT code, description, kind, value, max_discount_paise, min_subtotal_paise, applies_to, active,
                       valid_from, valid_to, usage_limit, used_count FROM coupons WHERE code = :c
                """).param("c", code).query((rs, i) -> {
            long max = rs.getLong(5);
            Long maxDiscount = rs.wasNull() ? null : max;
            Timestamp to = rs.getTimestamp(10);
            int limit = rs.getInt(11);
            Integer usage = rs.wasNull() ? null : limit;
            return new Coupon(rs.getString(1), rs.getString(2), Coupon.Kind.valueOf(rs.getString(3)), rs.getLong(4),
                    maxDiscount, rs.getLong(6), Coupon.AppliesTo.valueOf(rs.getString(7)), rs.getBoolean(8),
                    rs.getTimestamp(9).toInstant(), to == null ? null : to.toInstant(), usage, rs.getInt(12));
        }).optional();
    }

    /** Atomically consumes one use of a coupon; false if the limit was reached concurrently. */
    public boolean redeemCoupon(String code, UUID sessionId, String owner) {
        int updated = jdbc.sql("""
                UPDATE coupons SET used_count = used_count + 1
                WHERE code = :c AND active AND (usage_limit IS NULL OR used_count < usage_limit)
                """).param("c", code).update();
        if (updated == 0) return false;
        jdbc.sql("INSERT INTO coupon_redemptions (coupon_code, session_id, owner_hash) VALUES (:c, :s, :o)")
                .param("c", code).param("s", sessionId).param("o", owner).update();
        return true;
    }

    public void unredeemCoupon(UUID sessionId) {
        jdbc.sql("""
                WITH removed AS (DELETE FROM coupon_redemptions WHERE session_id = :s RETURNING coupon_code)
                UPDATE coupons SET used_count = used_count - 1 WHERE code IN (SELECT coupon_code FROM removed)
                """).param("s", sessionId).update();
    }

    // ---------- quotes ----------
    public record StoredQuote(UUID id, String ownerHash, String cartType, String pincode, String couponCode,
                              String fingerprint, String payload, long totalPaise, String signature, Instant expiresAt) {}

    public void saveQuote(StoredQuote q) {
        jdbc.sql("""
                INSERT INTO quotes (id, owner_hash, cart_type, pincode, coupon_code, fingerprint, payload, total_paise, signature, expires_at)
                VALUES (:id, :o, :t, :p, :c, :f, CAST(:payload AS jsonb), :total, :sig, :exp)
                """).param("id", q.id()).param("o", q.ownerHash()).param("t", q.cartType()).param("p", q.pincode())
                .param("c", q.couponCode()).param("f", q.fingerprint()).param("payload", q.payload())
                .param("total", q.totalPaise()).param("sig", q.signature()).param("exp", Timestamp.from(q.expiresAt()))
                .update();
    }

    public Optional<StoredQuote> quote(UUID id) {
        return jdbc.sql("""
                SELECT id, owner_hash, cart_type, pincode, coupon_code, fingerprint, payload::text, total_paise, signature, expires_at
                FROM quotes WHERE id = :id
                """).param("id", id).query((rs, i) -> new StoredQuote(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8),
                rs.getString(9), rs.getTimestamp(10).toInstant())).optional();
    }

    // ---------- sessions (idempotency) ----------
    public record Session(UUID id, String requestHash, String status, String response, String errorCode, UUID orderId) {}

    /** @return true if this call created the session row (i.e. owns processing for this key). */
    public boolean startSession(UUID id, String owner, String key, String requestHash, UUID quoteId, String method) {
        return jdbc.sql("""
                INSERT INTO checkout_sessions (id, owner_hash, idempotency_key, request_hash, quote_id, payment_method, status)
                VALUES (:id, :o, :k, :h, :q, :m, 'PROCESSING') ON CONFLICT (owner_hash, idempotency_key) DO NOTHING
                """).param("id", id).param("o", owner).param("k", key).param("h", requestHash).param("q", quoteId)
                .param("m", method).update() == 1;
    }

    public Optional<Session> session(String owner, String key) {
        return jdbc.sql("""
                SELECT id, request_hash, status, response::text, error_code, order_id FROM checkout_sessions
                WHERE owner_hash = :o AND idempotency_key = :k
                """).param("o", owner).param("k", key).query((rs, i) -> new Session(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class))).optional();
    }

    public void setSessionOrder(UUID id, UUID orderId) {
        jdbc.sql("UPDATE checkout_sessions SET order_id = :o, updated_at = now() WHERE id = :id")
                .param("o", orderId).param("id", id).update();
    }

    public void completeSession(UUID id, String responseJson) {
        jdbc.sql("UPDATE checkout_sessions SET status = 'COMPLETED', response = CAST(:r AS jsonb), updated_at = now() WHERE id = :id")
                .param("r", responseJson).param("id", id).update();
    }

    public void failSession(UUID id, String errorCode, String responseJson) {
        jdbc.sql("UPDATE checkout_sessions SET status = 'FAILED', error_code = :e, response = CAST(:r AS jsonb), updated_at = now() WHERE id = :id")
                .param("e", errorCode).param("r", responseJson).param("id", id).update();
    }
}
