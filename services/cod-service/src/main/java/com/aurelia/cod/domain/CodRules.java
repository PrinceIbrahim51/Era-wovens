package com.aurelia.cod.domain;

import com.aurelia.cod.config.CodProperties;
import java.util.ArrayList;
import java.util.List;

/** Pure COD eligibility rules. */
public final class CodRules {
    private CodRules() {}

    public record Facts(String pincode, Long orderValuePaise, String cartType, int pendingForOwner,
                        int pendingForPhone, int recentRtoForPhone) {}
    public record Decision(boolean eligible, long feePaise, List<String> reasons) {}

    public static Decision evaluate(Facts f, CodProperties p) {
        List<String> reasons = new ArrayList<>();
        if (f.pincode() == null || !f.pincode().matches("^[1-9][0-9]{5}$")
                || p.blockedPincodePrefixes().stream().anyMatch(f.pincode()::startsWith)) {
            reasons.add("PINCODE_NOT_ELIGIBLE");
        }
        if ("BULK".equals(f.cartType()) && !p.allowBulk()) reasons.add("BULK_ORDERS_NOT_ELIGIBLE");
        if (f.orderValuePaise() != null && f.orderValuePaise() + p.feePaise() > p.maxOrderValuePaise()) {
            reasons.add("ORDER_VALUE_ABOVE_LIMIT");
        }
        if (f.pendingForOwner() >= p.maxPendingOrders() || f.pendingForPhone() >= p.maxPendingOrders()) {
            reasons.add("TOO_MANY_PENDING_COD_ORDERS");
        }
        if (f.recentRtoForPhone() >= p.maxRecentRto() && p.maxRecentRto() > 0) reasons.add("RECENT_RETURNS_TO_ORIGIN");
        return new Decision(reasons.isEmpty(), p.feePaise(), List.copyOf(reasons));
    }
}
