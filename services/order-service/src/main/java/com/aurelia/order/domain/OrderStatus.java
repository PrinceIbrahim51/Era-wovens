package com.aurelia.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Explicit order state machine. Happy path:
 * DRAFT -> PENDING_PAYMENT | COD_CONFIRMED -> CONFIRMED -> FULFILLING -> SHIPPED -> DELIVERED.
 */
public enum OrderStatus {
    DRAFT, PENDING_PAYMENT, COD_CONFIRMED, CONFIRMED, FULFILLING, SHIPPED, DELIVERED,
    REVIEW_REQUIRED, CANCELLED, EXPIRED, RETURNED, REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.ofEntries(
            Map.entry(DRAFT, EnumSet.of(PENDING_PAYMENT, COD_CONFIRMED, CANCELLED, EXPIRED)),
            Map.entry(PENDING_PAYMENT, EnumSet.of(CONFIRMED, CANCELLED, EXPIRED, REVIEW_REQUIRED)),
            Map.entry(COD_CONFIRMED, EnumSet.of(CONFIRMED, CANCELLED, REVIEW_REQUIRED)),
            Map.entry(CONFIRMED, EnumSet.of(FULFILLING, CANCELLED)),
            Map.entry(FULFILLING, EnumSet.of(SHIPPED, CANCELLED)),
            Map.entry(SHIPPED, EnumSet.of(DELIVERED, RETURNED)),
            Map.entry(DELIVERED, EnumSet.of(RETURNED)),
            Map.entry(REVIEW_REQUIRED, EnumSet.of(CONFIRMED, CANCELLED, REFUNDED)),
            Map.entry(EXPIRED, EnumSet.of(REVIEW_REQUIRED)), // e.g. payment captured after the window closed
            Map.entry(CANCELLED, EnumSet.of(REFUNDED)),
            Map.entry(RETURNED, EnumSet.of(REFUNDED)),
            Map.entry(REFUNDED, EnumSet.noneOf(OrderStatus.class)));

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    /** Statuses holding reserved (not yet committed) stock. */
    public boolean holdsReservation() {
        return this == DRAFT || this == PENDING_PAYMENT || this == COD_CONFIRMED;
    }
}
