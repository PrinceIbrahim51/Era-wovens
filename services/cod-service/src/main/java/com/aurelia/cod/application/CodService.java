package com.aurelia.cod.application;

import com.aurelia.cod.config.CodProperties;
import com.aurelia.cod.domain.CodRules;
import com.aurelia.platform.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodService {
    private final JdbcClient jdbc;
    private final CodProperties props;

    public CodService(JdbcClient jdbc, CodProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public CodRules.Decision eligibility(String owner, String pincode, Long orderValuePaise, String cartType, String phone) {
        int pendingOwner = owner == null ? 0 : count("owner_hash = :v AND status = 'PENDING_COLLECTION'", owner);
        int pendingPhone = 0, rto = 0;
        if (phone != null) {
            String ph = phoneHash(phone);
            pendingPhone = count("phone_hash = :v AND status = 'PENDING_COLLECTION'", ph);
            rto = jdbc.sql("SELECT count(*) FROM cod_confirmations WHERE phone_hash = :v AND status = 'RTO' AND updated_at > :since")
                    .param("v", ph).param("since", Timestamp.from(Instant.now().minus(props.rtoLookback())))
                    .query(Integer.class).single();
        }
        return CodRules.evaluate(new CodRules.Facts(pincode, orderValuePaise, cartType, pendingOwner, pendingPhone, rto), props);
    }

    public record Confirmation(UUID orderId, String status, long orderValuePaise, long feePaise) {}

    /** Idempotent by orderId. orderValuePaise is the full payable amount including the COD fee. */
    @Transactional
    public Confirmation confirm(UUID orderId, String owner, String pincode, long orderValuePaise, long feePaise,
                                String cartType, String phone) {
        var existing = find(orderId);
        if (existing != null) return existing;
        if (feePaise != props.feePaise()) {
            throw new ApiException(HttpStatus.CONFLICT, "COD_FEE_CHANGED", "The COD fee changed; please review your total.");
        }
        var decision = eligibility(owner, pincode, orderValuePaise - feePaise, cartType, phone);
        if (!decision.eligible()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COD_NOT_ELIGIBLE",
                    "Cash on delivery is not available for this order.", Map.of("reasons", decision.reasons()));
        }
        try {
            jdbc.sql("""
                    INSERT INTO cod_confirmations (order_id, owner_hash, phone_hash, pincode, order_value_paise, fee_paise, status)
                    VALUES (:o, :owner, :ph, :pin, :v, :f, 'PENDING_COLLECTION')
                    """).param("o", orderId).param("owner", owner).param("ph", phoneHash(phone)).param("pin", pincode)
                    .param("v", orderValuePaise).param("f", feePaise).update();
        } catch (DuplicateKeyException e) {
            return find(orderId);
        }
        return find(orderId);
    }

    /** Records the single final outcome of a COD shipment (collected, returned to origin, or cancelled). */
    @Transactional
    public Confirmation recordOutcome(UUID orderId, String outcome, long collectedPaise, String actor, String note) {
        var c = jdbc.sql("SELECT status, order_value_paise FROM cod_confirmations WHERE order_id = :o FOR UPDATE")
                .param("o", orderId).query((rs, i) -> new Object[]{rs.getString(1), rs.getLong(2)}).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COD_NOT_FOUND", "COD confirmation not found."));
        if (!"PENDING_COLLECTION".equals(c[0])) {
            if (outcome.equals(c[0])) return find(orderId);
            throw new ApiException(HttpStatus.CONFLICT, "COD_ALREADY_SETTLED", "COD outcome already recorded: " + c[0]);
        }
        if ("COLLECTED".equals(outcome) && collectedPaise != (long) c[1]) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COLLECTION_AMOUNT_MISMATCH",
                    "Collected amount must equal the order value.", Map.of("status", "PENDING_COLLECTION"));
        }
        jdbc.sql("UPDATE cod_confirmations SET status = :s, updated_at = now() WHERE order_id = :o")
                .param("s", outcome).param("o", orderId).update();
        jdbc.sql("INSERT INTO cod_collections (order_id, outcome, collected_paise, actor, note) VALUES (:o, :s, :c, :a, :n)")
                .param("o", orderId).param("s", outcome).param("c", "COLLECTED".equals(outcome) ? collectedPaise : 0)
                .param("a", actor).param("n", note).update();
        return find(orderId);
    }

    private Confirmation find(UUID orderId) {
        return jdbc.sql("SELECT order_id, status, order_value_paise, fee_paise FROM cod_confirmations WHERE order_id = :o")
                .param("o", orderId).query((rs, i) -> new Confirmation(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getLong(3), rs.getLong(4))).optional().orElse(null);
    }

    private int count(String where, String value) {
        return jdbc.sql("SELECT count(*) FROM cod_confirmations WHERE " + where).param("v", value).query(Integer.class).single();
    }

    String phoneHash(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.phonePepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
