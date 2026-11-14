package com.aurelia.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelia.checkout.config.CheckoutProperties;
import com.aurelia.checkout.domain.CartType;
import com.aurelia.checkout.domain.Coupon;
import com.aurelia.checkout.domain.Money;
import com.aurelia.checkout.domain.Pincodes;
import com.aurelia.checkout.domain.PricingEngine;
import com.aurelia.checkout.domain.PricingEngine.Input;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingEngineTest {
    final PricingEngine engine = new PricingEngine(
            new CheckoutProperties.Tax(500, 1800, 250_000),
            new CheckoutProperties.Shipping(9_900, 299_900, 49_900, 2_500_000));
    final Instant now = Instant.parse("2026-11-14T00:00:00Z");

    @Test
    void inclusiveTaxIsExtractedNotAdded() {
        // ₹1,050 inclusive at 5% contains ₹50 tax.
        assertThat(Money.inclusiveTax(105_000, 500)).isEqualTo(5_000);
        assertThat(Money.inclusiveTax(118_000, 1800)).isEqualTo(18_000);
        assertThat(Money.inclusiveTax(1, 500)).isEqualTo(0);
    }

    @Test
    void regularOrderBelowThresholdPaysShipping() {
        var t = engine.price(CartType.REGULAR, List.of(new Input("A", "Saree", "Red", 145_000, 1)), 0);
        assertThat(t.subtotalPaise()).isEqualTo(145_000);
        assertThat(t.shippingPaise()).isEqualTo(9_900);
        assertThat(t.totalPaise()).isEqualTo(154_900);
        assertThat(t.lines().getFirst().taxRateBp()).isEqualTo(500);
    }

    @Test
    void highValuePieceUsesHigherRateAndFreeShipping() {
        var t = engine.price(CartType.REGULAR, List.of(new Input("A", "Silk", "Maroon", 2_850_000, 1)), 0);
        assertThat(t.shippingPaise()).isZero();
        assertThat(t.lines().getFirst().taxRateBp()).isEqualTo(1800);
        assertThat(t.taxIncludedPaise()).isEqualTo(Money.inclusiveTax(2_850_000, 1800));
    }

    @Test
    void discountAllocationSumsExactly() {
        var t = engine.price(CartType.REGULAR, List.of(
                new Input("A", "a", "x", 33_333, 1), new Input("B", "b", "y", 33_333, 2), new Input("C", "c", "z", 10_001, 3)), 10_007);
        assertThat(t.lines().stream().mapToLong(PricingEngine.PricedLine::discountPaise).sum()).isEqualTo(10_007);
        assertThat(t.totalPaise()).isEqualTo(t.subtotalPaise() - 10_007 + t.shippingPaise());
    }

    @Test
    void discountCanPushRegularOrderBelowFreeShipping() {
        var t = engine.price(CartType.REGULAR, List.of(new Input("A", "a", "x", 300_000, 1)), 10_000);
        assertThat(t.shippingPaise()).isEqualTo(9_900);
    }

    @Test
    void bulkShippingRules() {
        assertThat(engine.shippingFee(CartType.BULK, 2_499_999)).isEqualTo(49_900);
        assertThat(engine.shippingFee(CartType.BULK, 2_500_000)).isZero();
    }

    @Test
    void rejectsInvalidDiscountOrLines() {
        assertThatThrownBy(() -> engine.price(CartType.REGULAR, List.of(new Input("A", "a", "x", 100, 1)), 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.price(CartType.REGULAR, List.of(new Input("A", "a", "x", 100, 0)), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void couponRules() {
        var welcome = new Coupon("WELCOME10", "d", Coupon.Kind.PERCENT, 1000, 100_000L, 200_000, Coupon.AppliesTo.REGULAR,
                true, now.minusSeconds(60), null, 10, 0);
        assertThat(welcome.evaluate(500_000, CartType.REGULAR, now).discountPaise()).isEqualTo(50_000);
        assertThat(welcome.evaluate(5_000_000, CartType.REGULAR, now).discountPaise()).isEqualTo(100_000); // capped
        assertThat(welcome.evaluate(199_999, CartType.REGULAR, now).errorCode()).isEqualTo("COUPON_MIN_SUBTOTAL");
        assertThat(welcome.evaluate(500_000, CartType.BULK, now).errorCode()).isEqualTo("COUPON_NOT_APPLICABLE");

        var exhausted = new Coupon("X", "d", Coupon.Kind.FLAT, 50_000, null, 0, Coupon.AppliesTo.ALL, true,
                now.minusSeconds(60), null, 5, 5);
        assertThat(exhausted.evaluate(500_000, CartType.REGULAR, now).errorCode()).isEqualTo("COUPON_EXHAUSTED");
        var expired = new Coupon("Y", "d", Coupon.Kind.FLAT, 50_000, null, 0, Coupon.AppliesTo.ALL, true,
                now.minusSeconds(120), now.minusSeconds(60), null, 0);
        assertThat(expired.evaluate(500_000, CartType.REGULAR, now).errorCode()).isEqualTo("COUPON_INACTIVE");
        var flatBig = new Coupon("Z", "d", Coupon.Kind.FLAT, 50_000, null, 0, Coupon.AppliesTo.ALL, true,
                now.minusSeconds(60), null, null, 0);
        assertThat(flatBig.evaluate(10_000, CartType.REGULAR, now).discountPaise()).isEqualTo(10_000); // never negative
    }

    @Test
    void pincodes() {
        assertThat(Pincodes.isValid("560001")).isTrue();
        assertThat(Pincodes.isValid("060001")).isFalse();
        assertThat(Pincodes.isValid("56001")).isFalse();
        assertThat(Pincodes.isServiceable("744101", List.of("744"))).isFalse();
        assertThat(Pincodes.isServiceable("560001", List.of("744"))).isTrue();
    }
}
