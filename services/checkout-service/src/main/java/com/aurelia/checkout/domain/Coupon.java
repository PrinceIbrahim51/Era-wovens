package com.aurelia.checkout.domain;

import java.time.Instant;

public record Coupon(String code, String description, Kind kind, long value, Long maxDiscountPaise,
                     long minSubtotalPaise, AppliesTo appliesTo, boolean active, Instant validFrom, Instant validTo,
                     Integer usageLimit, int usedCount) {

    public enum Kind { PERCENT, FLAT }
    public enum AppliesTo { ALL, REGULAR, BULK }

    public record Evaluation(long discountPaise, String errorCode, String message) {
        public boolean applied() { return errorCode == null; }
    }

    public Evaluation evaluate(long subtotalPaise, CartType cartType, Instant now) {
        if (!active || now.isBefore(validFrom) || (validTo != null && !now.isBefore(validTo))) {
            return new Evaluation(0, "COUPON_INACTIVE", "This coupon is not active.");
        }
        if (usageLimit != null && usedCount >= usageLimit) {
            return new Evaluation(0, "COUPON_EXHAUSTED", "This coupon has reached its usage limit.");
        }
        if (appliesTo != AppliesTo.ALL && !appliesTo.name().equals(cartType.name())) {
            return new Evaluation(0, "COUPON_NOT_APPLICABLE",
                    cartType == CartType.BULK ? "This coupon is not valid on bulk orders." : "This coupon is only valid on bulk orders.");
        }
        if (subtotalPaise < minSubtotalPaise) {
            return new Evaluation(0, "COUPON_MIN_SUBTOTAL", "Add more items to use this coupon.");
        }
        long discount = kind == Kind.PERCENT ? Money.percentOf(subtotalPaise, value) : value;
        if (maxDiscountPaise != null) discount = Math.min(discount, maxDiscountPaise);
        discount = Math.min(discount, subtotalPaise);
        return new Evaluation(discount, null, description);
    }
}
