package com.aurelia.cod;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelia.cod.config.CodProperties;
import com.aurelia.cod.domain.CodRules;
import com.aurelia.cod.domain.CodRules.Facts;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodRulesTest {
    final CodProperties props = new CodProperties(4_900, 2_500_000, false, 3, 2, Duration.ofDays(90),
            List.of("744"), "x".repeat(32));

    @Test
    void eligibleOrder() {
        var d = CodRules.evaluate(new Facts("560001", 1_000_000L, "REGULAR", 0, 0, 0), props);
        assertThat(d.eligible()).isTrue();
        assertThat(d.feePaise()).isEqualTo(4_900);
    }

    @Test
    void ruleViolationsAreReported() {
        assertThat(CodRules.evaluate(new Facts("744101", 100L, "REGULAR", 0, 0, 0), props).reasons())
                .containsExactly("PINCODE_NOT_ELIGIBLE");
        assertThat(CodRules.evaluate(new Facts("560001", 100L, "BULK", 0, 0, 0), props).reasons())
                .containsExactly("BULK_ORDERS_NOT_ELIGIBLE");
        assertThat(CodRules.evaluate(new Facts("560001", 2_495_101L, "REGULAR", 0, 0, 0), props).reasons())
                .containsExactly("ORDER_VALUE_ABOVE_LIMIT");
        assertThat(CodRules.evaluate(new Facts("560001", 100L, "REGULAR", 3, 0, 0), props).reasons())
                .containsExactly("TOO_MANY_PENDING_COD_ORDERS");
        assertThat(CodRules.evaluate(new Facts("560001", 100L, "REGULAR", 0, 0, 2), props).reasons())
                .containsExactly("RECENT_RETURNS_TO_ORIGIN");
    }

    @Test
    void valueLimitIncludesFee() {
        assertThat(CodRules.evaluate(new Facts("560001", 2_495_100L, "REGULAR", 0, 0, 0), props).eligible()).isTrue();
    }
}
