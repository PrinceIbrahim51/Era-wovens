package com.aurelia.order;

import static com.aurelia.order.domain.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.aurelia.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

class OrderStatusTest {
    @Test
    void happyPaths() {
        assertThat(DRAFT.canTransitionTo(PENDING_PAYMENT)).isTrue();
        assertThat(DRAFT.canTransitionTo(COD_CONFIRMED)).isTrue();
        assertThat(PENDING_PAYMENT.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(COD_CONFIRMED.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(FULFILLING)).isTrue();
        assertThat(FULFILLING.canTransitionTo(SHIPPED)).isTrue();
        assertThat(SHIPPED.canTransitionTo(DELIVERED)).isTrue();
    }

    @Test
    void illegalShortcutsAreRejected() {
        assertThat(DRAFT.canTransitionTo(CONFIRMED)).isFalse();          // must pass through payment/COD
        assertThat(PENDING_PAYMENT.canTransitionTo(COD_CONFIRMED)).isFalse(); // payment methods are exclusive
        assertThat(COD_CONFIRMED.canTransitionTo(PENDING_PAYMENT)).isFalse();
        assertThat(EXPIRED.canTransitionTo(CONFIRMED)).isFalse();
        assertThat(CANCELLED.canTransitionTo(CONFIRMED)).isFalse();
        assertThat(DELIVERED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(SHIPPED.canTransitionTo(PENDING_PAYMENT)).isFalse();
    }

    @Test
    void terminalStatesAreTerminal() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(REFUNDED.canTransitionTo(s)).isFalse();
        }
    }

    @Test
    void onlyPrePaymentStatesHoldReservations() {
        assertThat(PENDING_PAYMENT.holdsReservation()).isTrue();
        assertThat(CONFIRMED.holdsReservation()).isFalse();
    }
}
