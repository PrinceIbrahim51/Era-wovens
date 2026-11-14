package com.aurelia.checkout.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Integer paise arithmetic with explicit rounding. */
public final class Money {
    private Money() {}

    /** Tax contained in a tax-inclusive amount: amount * rate / (1 + rate), HALF_UP to the paisa. */
    public static long inclusiveTax(long amountPaise, int rateBp) {
        if (rateBp == 0 || amountPaise == 0) return 0;
        return BigDecimal.valueOf(amountPaise).multiply(BigDecimal.valueOf(rateBp))
                .divide(BigDecimal.valueOf(10_000L + rateBp), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** amount * bp / 10000, rounded down so discounts never exceed the stated percentage. */
    public static long percentOf(long amountPaise, long bp) {
        return BigDecimal.valueOf(amountPaise).multiply(BigDecimal.valueOf(bp))
                .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.DOWN).longValueExact();
    }
}
