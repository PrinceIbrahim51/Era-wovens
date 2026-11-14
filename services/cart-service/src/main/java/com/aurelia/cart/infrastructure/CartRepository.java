package com.aurelia.cart.infrastructure;

import com.aurelia.cart.domain.CartType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CartRepository {
    private final JdbcClient jdbc;

    public CartRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record CartRow(UUID id, String ownerHash, CartType type, long version) {}
    public record ItemRow(UUID id, String sku, int quantity, Instant createdAt) {}

    public Optional<CartRow> find(String owner, CartType type) {
        return jdbc.sql("SELECT id, owner_hash, cart_type, version FROM carts WHERE owner_hash = :o AND cart_type = :t AND expires_at > now()")
                .param("o", owner).param("t", type.name())
                .query((rs, i) -> new CartRow(rs.getObject(1, UUID.class), rs.getString(2),
                        CartType.valueOf(rs.getString(3)), rs.getLong(4))).optional();
    }

    /** Get or create, locking the cart row so concurrent edits on one cart serialise. */
    public CartRow lockOrCreate(String owner, CartType type, Duration ttl) {
        jdbc.sql("""
                INSERT INTO carts (owner_hash, cart_type, expires_at) VALUES (:o, :t, :exp)
                ON CONFLICT (owner_hash, cart_type) DO NOTHING
                """).param("o", owner).param("t", type.name()).param("exp", java.sql.Timestamp.from(Instant.now().plus(ttl))).update();
        return jdbc.sql("SELECT id, owner_hash, cart_type, version FROM carts WHERE owner_hash = :o AND cart_type = :t FOR UPDATE")
                .param("o", owner).param("t", type.name())
                .query((rs, i) -> new CartRow(rs.getObject(1, UUID.class), rs.getString(2),
                        CartType.valueOf(rs.getString(3)), rs.getLong(4))).single();
    }

    public void touch(UUID cartId, Duration ttl) {
        jdbc.sql("UPDATE carts SET version = version + 1, updated_at = now(), expires_at = :exp WHERE id = :id")
                .param("exp", java.sql.Timestamp.from(Instant.now().plus(ttl))).param("id", cartId).update();
    }

    public List<ItemRow> items(UUID cartId) {
        return jdbc.sql("SELECT id, sku, quantity, created_at FROM cart_items WHERE cart_id = :c ORDER BY created_at, sku")
                .param("c", cartId)
                .query((rs, i) -> new ItemRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
                        rs.getTimestamp(4).toInstant())).list();
    }

    public Optional<ItemRow> item(UUID cartId, UUID itemId) {
        return jdbc.sql("SELECT id, sku, quantity, created_at FROM cart_items WHERE cart_id = :c AND id = :i")
                .param("c", cartId).param("i", itemId)
                .query((rs, i) -> new ItemRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
                        rs.getTimestamp(4).toInstant())).optional();
    }

    /** Finds which of the owner's carts holds an item (ownership check for PATCH/DELETE). */
    public Optional<CartType> cartTypeOfItem(String owner, UUID itemId) {
        return jdbc.sql("""
                SELECT c.cart_type FROM cart_items i JOIN carts c ON c.id = i.cart_id
                WHERE i.id = :i AND c.owner_hash = :o
                """).param("i", itemId).param("o", owner).query(String.class).optional().map(CartType::valueOf);
    }

    public void upsertQuantity(UUID cartId, String sku, int quantity) {
        jdbc.sql("""
                INSERT INTO cart_items (cart_id, sku, quantity) VALUES (:c, :s, :q)
                ON CONFLICT (cart_id, sku) DO UPDATE SET quantity = :q, updated_at = now()
                """).param("c", cartId).param("s", sku).param("q", quantity).update();
    }

    public void setQuantity(UUID itemId, int quantity) {
        jdbc.sql("UPDATE cart_items SET quantity = :q, updated_at = now() WHERE id = :i")
                .param("q", quantity).param("i", itemId).update();
    }

    public void delete(UUID itemId) {
        jdbc.sql("DELETE FROM cart_items WHERE id = :i").param("i", itemId).update();
    }

    public int deleteExpired() {
        return jdbc.sql("DELETE FROM carts WHERE expires_at < now()").update();
    }
}
