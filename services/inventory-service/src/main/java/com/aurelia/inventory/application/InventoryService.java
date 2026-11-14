package com.aurelia.inventory.application;

import com.aurelia.inventory.config.ReservationProperties;
import com.aurelia.inventory.domain.ReservationStatus;
import com.aurelia.platform.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock reservations use a single conditional UPDATE per SKU ({@code WHERE on_hand - reserved >= qty}),
 * which is atomic under concurrency and cannot oversell. Lines are applied in SKU order to avoid deadlocks.
 * All operations are idempotent by {@code reference} (the order id).
 */
@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final JdbcClient jdbc;
    private final ReservationProperties props;

    public InventoryService(JdbcClient jdbc, ReservationProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public record Line(String sku, int quantity) {}
    public record Reservation(String reference, ReservationStatus status, Instant expiresAt, List<Line> lines) {}

    public Map<String, Integer> availability(List<String> skus) {
        Map<String, Integer> result = new LinkedHashMap<>();
        skus.forEach(s -> result.put(s, 0));
        if (skus.isEmpty()) return result;
        jdbc.sql("SELECT sku, on_hand - reserved FROM stock_levels WHERE sku IN (:skus)").param("skus", skus)
                .query(rs -> { result.put(rs.getString(1), rs.getInt(2)); });
        return result;
    }

    @Transactional
    public Reservation reserve(String reference, List<Line> requested, Duration ttl) {
        var existing = find(reference);
        if (!existing.lines().isEmpty()) {
            return existing; // idempotent replay
        }
        Duration effectiveTtl = ttl == null ? props.defaultTtl() : ttl;
        if (effectiveTtl.compareTo(props.maxTtl()) > 0 || effectiveTtl.isNegative() || effectiveTtl.isZero()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TTL", "Reservation TTL out of range.");
        }
        Map<String, Integer> merged = new HashMap<>();
        requested.forEach(l -> merged.merge(l.sku(), l.quantity(), Integer::sum));
        List<Line> lines = merged.entrySet().stream().map(e -> new Line(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(Line::sku)).toList();
        Instant expiresAt = Instant.now().plus(effectiveTtl);

        List<String> insufficient = new ArrayList<>();
        for (Line line : lines) {
            int updated = jdbc.sql("""
                    UPDATE stock_levels SET reserved = reserved + :qty, version = version + 1, updated_at = now()
                    WHERE sku = :sku AND on_hand - reserved >= :qty
                    """).param("qty", line.quantity()).param("sku", line.sku()).update();
            if (updated == 0) insufficient.add(line.sku());
        }
        if (!insufficient.isEmpty()) {
            // Throwing rolls back every reservation made in this transaction (all-or-nothing).
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Some items no longer have enough stock.", Map.of("skus", insufficient));
        }
        for (Line line : lines) {
            jdbc.sql("""
                    INSERT INTO stock_reservations (reference, sku, quantity, status, expires_at)
                    VALUES (:ref, :sku, :qty, 'ACTIVE', :exp)
                    """).param("ref", reference).param("sku", line.sku()).param("qty", line.quantity())
                    .param("exp", java.sql.Timestamp.from(expiresAt)).update();
            movement(line.sku(), 0, line.quantity(), "RESERVE", reference);
        }
        return new Reservation(reference, ReservationStatus.ACTIVE, expiresAt, lines);
    }

    /** Converts an active reservation into a stock decrement. Idempotent once committed. */
    @Transactional
    public Reservation commit(String reference) {
        var current = lock(reference);
        if (current.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation not found.");
        }
        ReservationStatus status = current.getFirst().status;
        if (status == ReservationStatus.COMMITTED) return find(reference);
        if (status != ReservationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_ACTIVE",
                    "Reservation is " + status + " and cannot be committed.", Map.of("status", status.name()));
        }
        for (Row r : current) {
            jdbc.sql("""
                    UPDATE stock_levels SET on_hand = on_hand - :qty, reserved = reserved - :qty,
                           version = version + 1, updated_at = now() WHERE sku = :sku
                    """).param("qty", r.quantity).param("sku", r.sku).update();
            movement(r.sku, -r.quantity, -r.quantity, "COMMIT", reference);
        }
        setStatus(reference, ReservationStatus.COMMITTED);
        return find(reference);
    }

    /** Returns reserved stock to the available pool. Idempotent; committed reservations cannot be released. */
    @Transactional
    public Reservation release(String reference, ReservationStatus target) {
        var current = lock(reference);
        if (current.isEmpty()) {
            return new Reservation(reference, ReservationStatus.RELEASED, null, List.of());
        }
        ReservationStatus status = current.getFirst().status;
        if (status == ReservationStatus.COMMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_COMMITTED", "Committed stock cannot be released.");
        }
        if (status == ReservationStatus.ACTIVE) {
            for (Row r : current) {
                jdbc.sql("""
                        UPDATE stock_levels SET reserved = reserved - :qty, version = version + 1, updated_at = now()
                        WHERE sku = :sku
                        """).param("qty", r.quantity).param("sku", r.sku).update();
                movement(r.sku, 0, -r.quantity, target == ReservationStatus.EXPIRED ? "EXPIRE" : "RELEASE", reference);
            }
            setStatus(reference, target);
        }
        return find(reference);
    }

    /** Releases expired reservations; SKIP LOCKED lets several instances sweep safely. */
    @Transactional
    public int expireDue(int batch) {
        List<String> refs = jdbc.sql("""
                SELECT DISTINCT reference FROM (
                  SELECT reference FROM stock_reservations WHERE status = 'ACTIVE' AND expires_at < now()
                  ORDER BY expires_at LIMIT :batch FOR UPDATE SKIP LOCKED) due
                """).param("batch", batch).query(String.class).list();
        refs.forEach(ref -> release(ref, ReservationStatus.EXPIRED));
        if (!refs.isEmpty()) log.info("Expired {} stock reservations", refs.size());
        return refs.size();
    }

    public Reservation find(String reference) {
        var rows = jdbc.sql("SELECT sku, quantity, status, expires_at FROM stock_reservations WHERE reference = :ref ORDER BY sku")
                .param("ref", reference).query(Row::map).list();
        if (rows.isEmpty()) return new Reservation(reference, null, null, List.of());
        return new Reservation(reference, rows.getFirst().status, rows.getFirst().expiresAt,
                rows.stream().map(r -> new Line(r.sku, r.quantity)).toList());
    }

    private List<Row> lock(String reference) {
        return jdbc.sql("""
                SELECT sku, quantity, status, expires_at FROM stock_reservations WHERE reference = :ref
                ORDER BY sku FOR UPDATE
                """).param("ref", reference).query(Row::map).list();
    }

    private void setStatus(String reference, ReservationStatus status) {
        jdbc.sql("UPDATE stock_reservations SET status = :s, updated_at = now() WHERE reference = :ref")
                .param("s", status.name()).param("ref", reference).update();
    }

    private void movement(String sku, int dOnHand, int dReserved, String reason, String reference) {
        jdbc.sql("""
                INSERT INTO stock_movements (sku, delta_on_hand, delta_reserved, reason, reference)
                VALUES (:sku, :a, :b, :reason, :ref)
                """).param("sku", sku).param("a", dOnHand).param("b", dReserved).param("reason", reason)
                .param("ref", reference).update();
    }

    private record Row(String sku, int quantity, ReservationStatus status, Instant expiresAt) {
        static Row map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
            return new Row(rs.getString(1), rs.getInt(2), ReservationStatus.valueOf(rs.getString(3)),
                    rs.getTimestamp(4).toInstant());
        }
    }
}
