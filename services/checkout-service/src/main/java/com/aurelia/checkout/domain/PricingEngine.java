package com.aurelia.checkout.domain;

import com.aurelia.checkout.config.CheckoutProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, server-side price calculation. Prices are GST-inclusive (Indian retail convention);
 * the tax component is computed per line on the post-discount value.
 */
public class PricingEngine {
    private final CheckoutProperties.Tax tax;
    private final CheckoutProperties.Shipping shipping;

    public PricingEngine(CheckoutProperties.Tax tax, CheckoutProperties.Shipping shipping) {
        this.tax = tax;
        this.shipping = shipping;
    }

    public record Input(String sku, String productName, String colorName, long unitPricePaise, int quantity) {}
    public record PricedLine(String sku, String productName, String colorName, long unitPricePaise, int quantity,
                             long lineTotalPaise, long discountPaise, int taxRateBp, long taxIncludedPaise) {}
    public record Totals(List<PricedLine> lines, long subtotalPaise, long discountPaise, long shippingPaise,
                         long taxIncludedPaise, long totalPaise) {}

    public Totals price(CartType type, List<Input> inputs, long discountPaise) {
        if (inputs.isEmpty()) throw new IllegalArgumentException("no lines");
        long subtotal = 0;
        long[] lineTotals = new long[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            var in = inputs.get(i);
            if (in.quantity() <= 0 || in.unitPricePaise() <= 0) throw new IllegalArgumentException("invalid line");
            lineTotals[i] = Math.multiplyExact(in.unitPricePaise(), (long) in.quantity());
            subtotal = Math.addExact(subtotal, lineTotals[i]);
        }
        if (discountPaise < 0 || discountPaise > subtotal) throw new IllegalArgumentException("invalid discount");
        long[] allocated = allocate(discountPaise, lineTotals, subtotal);

        List<PricedLine> lines = new ArrayList<>();
        long taxTotal = 0;
        for (int i = 0; i < inputs.size(); i++) {
            var in = inputs.get(i);
            long net = lineTotals[i] - allocated[i];
            int rate = (net / in.quantity()) <= tax.thresholdPerPiecePaise() ? tax.lowRateBp() : tax.highRateBp();
            long lineTax = Money.inclusiveTax(net, rate);
            taxTotal += lineTax;
            lines.add(new PricedLine(in.sku(), in.productName(), in.colorName(), in.unitPricePaise(), in.quantity(),
                    lineTotals[i], allocated[i], rate, lineTax));
        }
        long afterDiscount = subtotal - discountPaise;
        long ship = shippingFee(type, afterDiscount);
        return new Totals(lines, subtotal, discountPaise, ship, taxTotal, Math.addExact(afterDiscount, ship));
    }

    public long shippingFee(CartType type, long afterDiscountPaise) {
        return switch (type) {
            case REGULAR -> afterDiscountPaise >= shipping.regularFreeFromPaise() ? 0 : shipping.regularFeePaise();
            case BULK -> afterDiscountPaise >= shipping.bulkFreeFromPaise() ? 0 : shipping.bulkFeePaise();
        };
    }

    /** Proportional allocation; rounding remainder goes to the largest line so the sum is exact. */
    static long[] allocate(long discount, long[] lineTotals, long subtotal) {
        long[] out = new long[lineTotals.length];
        if (discount == 0) return out;
        long assigned = 0;
        int largest = 0;
        for (int i = 0; i < lineTotals.length; i++) {
            out[i] = java.math.BigInteger.valueOf(discount).multiply(java.math.BigInteger.valueOf(lineTotals[i]))
                    .divide(java.math.BigInteger.valueOf(subtotal)).longValueExact();
            assigned += out[i];
            if (lineTotals[i] > lineTotals[largest]) largest = i;
        }
        out[largest] += discount - assigned;
        return out;
    }
}
