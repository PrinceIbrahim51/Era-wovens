package com.aurelia.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelia.cart.config.CartProperties;
import com.aurelia.cart.domain.CartRules;
import com.aurelia.cart.domain.CartRules.SkuFacts;
import com.aurelia.cart.domain.CartType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CartRulesTest {
    final CartProperties.Bulk mixed = new CartProperties.Bulk(10, true, 500);
    final CartProperties.Bulk perSku = new CartProperties.Bulk(10, false, 500);

    @Test
    void regularLineRespectsPerOrderLimitAndStock() {
        var facts = new SkuFacts(true, true, false, 5, 3);
        assertThat(CartRules.validateLine(CartType.REGULAR, 2, facts, mixed)).isNull();
        assertThat(CartRules.validateLine(CartType.REGULAR, 4, facts, mixed)).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(CartRules.validateLine(CartType.REGULAR, 6, new SkuFacts(true, true, false, 5, 50), mixed))
                .isEqualTo("QUANTITY_LIMIT_EXCEEDED");
        assertThat(CartRules.validateLine(CartType.REGULAR, 1, new SkuFacts(true, true, false, 5, 0), mixed))
                .isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void bulkCartAcceptsOnlyBulkEligibleSkus() {
        assertThat(CartRules.validateLine(CartType.BULK, 20, new SkuFacts(true, true, false, 5, 100), mixed))
                .isEqualTo("NOT_BULK_ELIGIBLE");
        assertThat(CartRules.validateLine(CartType.BULK, 20, new SkuFacts(true, true, true, 10, 100), mixed)).isNull();
        assertThat(CartRules.validateLine(CartType.BULK, 501, new SkuFacts(true, true, true, 10, 1000), mixed))
                .isEqualTo("QUANTITY_LIMIT_EXCEEDED");
    }

    @Test
    void unknownOrInactiveSkuRejected() {
        assertThat(CartRules.validateLine(CartType.REGULAR, 1, new SkuFacts(false, false, false, 0, 0), mixed))
                .isEqualTo("SKU_UNAVAILABLE");
        assertThat(CartRules.validateLine(CartType.REGULAR, 1, new SkuFacts(true, false, false, 5, 9), mixed))
                .isEqualTo("SKU_UNAVAILABLE");
    }

    @Test
    void bulkMinimumAcrossMixedSkus() {
        assertThat(CartRules.bulkStatus(Map.of("A", 4, "B", 5), mixed).met()).isFalse();
        assertThat(CartRules.bulkStatus(Map.of("A", 4, "B", 6), mixed).met()).isTrue();
    }

    @Test
    void bulkMinimumPerSkuWhenMixingDisabled() {
        var status = CartRules.bulkStatus(Map.of("A", 4, "B", 12), perSku);
        assertThat(status.met()).isFalse();
        assertThat(status.skusBelowMinimum()).containsExactly("A");
        assertThat(CartRules.bulkStatus(Map.of("A", 10, "B", 12), perSku).met()).isTrue();
        assertThat(CartRules.bulkStatus(Map.of(), perSku).met()).isFalse();
    }
}
