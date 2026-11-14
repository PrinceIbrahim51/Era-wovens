package com.aurelia.order.infrastructure;

import com.aurelia.order.domain.OrderStatus;
import com.aurelia.order.domain.PaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record OrderRow(UUID id, String orderNumber, String ownerHash, String cartType, String paymentMethod,
                           OrderStatus status, PaymentStatus paymentStatus, String contactName, String contactEmail,
                           String contactPhone, String shippingAddressJson, long subtotalPaise, long discountPaise,
                           long shippingPaise, long codFeePaise, long taxIncludedPaise, long totalPaise,
                           String currency, String couponCode, String providerPaymentId, long version,
                           Instant expiresAt, Instant createdAt) {}
    public record ItemRow(String sku, String productName, String colorName, long unitPricePaise, int quantity,
                          long lineTotalPaise, long discountPaise, int taxRateBp, long taxIncludedPaise) {}
    public record EventRow(String fromStatus, String toStatus, String reason, Instant at) {}

    private static final String COLS = """
            id, order_number, owner_hash, cart_type, payment_method, status, payment_status, contact_name, contact_email,
            contact_phone, shipping_address::text, subtotal_paise, discount_paise, shipping_paise, cod_fee_paise,
            tax_included_paise, total_paise, currency, coupon_code, provider_payment_id, version, expires_at, created_at
            """;

    public void insert(OrderRow o, String idempotencyKey) {
        jdbc.sql("""
                INSERT INTO orders (id, order_number, idempotency_key, owner_hash, cart_type, payment_method, status,
                    payment_status, contact_name, contact_email, contact_phone, shipping_address, subtotal_paise,
                    discount_paise, shipping_paise, cod_fee_paise, tax_included_paise, total_paise, currency, coupon_code, expires_at)
                VALUES (:id, :num, :key, :owner, :cart, :pm, :st, :ps, :name, :email, :phone, CAST(:addr AS jsonb), :sub,
                    :disc, :ship, :cod, :tax, :total, :cur, :coupon, :exp)
                """).param("id", o.id()).param("num", o.orderNumber()).param("key", idempotencyKey)
                .param("owner", o.ownerHash()).param("cart", o.cartType()).param("pm", o.paymentMethod())
                .param("st", o.status().name()).param("ps", o.paymentStatus().name()).param("name", o.contactName())
                .param("email", o.contactEmail()).param("phone", o.contactPhone()).param("addr", o.shippingAddressJson())
                .param("sub", o.subtotalPaise()).param("disc", o.discountPaise()).param("ship", o.shippingPaise())
                .param("cod", o.codFeePaise()).param("tax", o.taxIncludedPaise()).param("total", o.totalPaise())
                .param("cur", o.currency()).param("coupon", o.couponCode())
                .param("exp", o.expiresAt() == null ? null : Timestamp.from(o.expiresAt())).update();
    }

    public void insertItem(UUID orderId, ItemRow i) {
        jdbc.sql("""
                INSERT INTO order_items (order_id, sku, product_name, color_name, unit_price_paise, quantity,
                    line_total_paise, discount_paise, tax_rate_bp, tax_included_paise)
                VALUES (:o, :sku, :name, :color, :unit, :qty, :line, :disc, :rate, :tax)
                """).param("o", orderId).param("sku", i.sku()).param("name", i.productName()).param("color", i.colorName())
                .param("unit", i.unitPricePaise()).param("qty", i.quantity()).param("line", i.lineTotalPaise())
                .param("disc", i.discountPaise()).param("rate", i.taxRateBp()).param("tax", i.taxIncludedPaise()).update();
    }

    public Optional<OrderRow> byId(UUID id) {
        return jdbc.sql("SELECT " + COLS + " FROM orders WHERE id = :id").param("id", id).query(this::map).optional();
    }

    public Optional<OrderRow> lockById(UUID id) {
        return jdbc.sql("SELECT " + COLS + " FROM orders WHERE id = :id FOR UPDATE").param("id", id).query(this::map).optional();
    }

    public Optional<OrderRow> byIdempotencyKey(String key) {
        return jdbc.sql("SELECT " + COLS + " FROM orders WHERE idempotency_key = :k").param("k", key).query(this::map).optional();
    }

    public Optional<OrderRow> byNumber(String number) {
        return jdbc.sql("SELECT " + COLS + " FROM orders WHERE order_number = :n").param("n", number).query(this::map).optional();
    }

    public List<OrderRow> byOwner(String owner, int limit) {
        return jdbc.sql("SELECT " + COLS + " FROM orders WHERE owner_hash = :o AND status <> 'DRAFT' ORDER BY created_at DESC LIMIT :l")
                .param("o", owner).param("l", limit).query(this::map).list();
    }

    public boolean numberExists(String number) {
        return jdbc.sql("SELECT count(*) FROM orders WHERE order_number = :n").param("n", number).query(Integer.class).single() > 0;
    }

    public List<ItemRow> items(UUID orderId) {
        return jdbc.sql("""
                SELECT sku, product_name, color_name, unit_price_paise, quantity, line_total_paise, discount_paise,
                       tax_rate_bp, tax_included_paise FROM order_items WHERE order_id = :o ORDER BY sku
                """).param("o", orderId).query((rs, i) -> new ItemRow(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getLong(4), rs.getInt(5), rs.getLong(6), rs.getLong(7), rs.getInt(8), rs.getLong(9))).list();
    }

    public List<EventRow> events(UUID orderId) {
        return jdbc.sql("SELECT from_status, to_status, reason, created_at FROM order_events WHERE order_id = :o ORDER BY id")
                .param("o", orderId).query((rs, i) -> new EventRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant())).list();
    }

    /** Optimistic update guarded by the expected version and current status. */
    public boolean updateStatus(UUID id, long version, OrderStatus from, OrderStatus to, PaymentStatus paymentStatus,
                                Instant expiresAt, String providerPaymentId) {
        return jdbc.sql("""
                UPDATE orders SET status = :to, payment_status = :ps, expires_at = :exp,
                       provider_payment_id = COALESCE(:pp, provider_payment_id), version = version + 1, updated_at = now()
                WHERE id = :id AND version = :v AND status = :from
                """).param("to", to.name()).param("ps", paymentStatus.name())
                .param("exp", expiresAt == null ? null : Timestamp.from(expiresAt)).param("pp", providerPaymentId)
                .param("id", id).param("v", version).param("from", from.name()).update() == 1;
    }

    public void event(UUID orderId, OrderStatus from, OrderStatus to, String reason, String actor) {
        jdbc.sql("INSERT INTO order_events (order_id, from_status, to_status, reason, actor) VALUES (:o, :f, :t, :r, :a)")
                .param("o", orderId).param("f", from == null ? null : from.name()).param("t", to.name())
                .param("r", reason).param("a", actor).update();
    }

    public void outbox(UUID aggregateId, String type, String payloadJson) {
        jdbc.sql("INSERT INTO outbox (aggregate_id, event_type, payload) VALUES (:a, :t, CAST(:p AS jsonb))")
                .param("a", aggregateId).param("t", type).param("p", payloadJson).update();
    }

    public List<UUID> dueForExpiry(int limit) {
        return jdbc.sql("""
                SELECT id FROM orders WHERE status IN ('DRAFT', 'PENDING_PAYMENT') AND expires_at < now()
                ORDER BY expires_at LIMIT :l
                """).param("l", limit).query(UUID.class).list();
    }

    private OrderRow map(ResultSet rs, int i) throws SQLException {
        Timestamp exp = rs.getTimestamp(22);
        return new OrderRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), OrderStatus.valueOf(rs.getString(6)), PaymentStatus.valueOf(rs.getString(7)),
                rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getLong(12), rs.getLong(13),
                rs.getLong(14), rs.getLong(15), rs.getLong(16), rs.getLong(17), rs.getString(18), rs.getString(19),
                rs.getString(20), rs.getLong(21), exp == null ? null : exp.toInstant(), rs.getTimestamp(23).toInstant());
    }
}
