package com.aurelia.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelia.payment.config.RazorpayProperties;
import org.junit.jupiter.api.Test;

class RazorpayPropertiesTest {
    @Test
    void refusesLiveModeAndLiveKeys() {
        assertThatThrownBy(() -> new RazorpayProperties(true, "live", "rzp_live_abcdefgh12", "s".repeat(20), "w".repeat(20), "https://api.razorpay.com", "x"))
                .hasMessageContaining("must be 'test'");
        assertThatThrownBy(() -> new RazorpayProperties(true, "test", "rzp_live_abcdefgh12", "s".repeat(20), "w".repeat(20), "https://api.razorpay.com", "x"))
                .hasMessageContaining("TEST key");
        assertThatThrownBy(() -> new RazorpayProperties(true, "test", "rzp_test_abcdefgh12", "", "w".repeat(20), "https://api.razorpay.com", "x"))
                .hasMessageContaining("KEY_SECRET");
        assertThatThrownBy(() -> new RazorpayProperties(true, "test", "rzp_test_abcdefgh12", "s".repeat(20), "s".repeat(20), "https://api.razorpay.com", "x"))
                .hasMessageContaining("WEBHOOK_SECRET");
    }

    @Test
    void disabledNeedsNoKeysButStillTestMode() {
        assertThatCode(() -> new RazorpayProperties(false, "test", "", "", "", null, "x")).doesNotThrowAnyException();
        assertThatThrownBy(() -> new RazorpayProperties(false, "live", "", "", "", null, "x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretsAreRedactedFromToString() {
        var p = new RazorpayProperties(true, "test", "rzp_test_abcdefgh12", "supersecretvalue123", "webhooksecret12345", "https://api.razorpay.com", "x");
        org.assertj.core.api.Assertions.assertThat(p.toString()).doesNotContain("supersecret").doesNotContain("webhooksecret");
    }
}
