package com.aurelia.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelia.payment.domain.Signatures;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Vectors generated independently with: printf '...' | openssl dgst -sha256 -hmac '<secret>' */
class SignaturesTest {
    @Test
    void checkoutSignatureMatchesOpenSslVector() {
        String expected = "1c5949633dad078edf7b627c7253a7dc7d47975db36ee8e3ae4d77927d04cf62";
        assertThat(Signatures.verifyPayment("order_TEST123456", "pay_TEST123456", expected, "test_secret_abcdefghij")).isTrue();
        assertThat(Signatures.verifyPayment("order_TEST123456", "pay_OTHER12345", expected, "test_secret_abcdefghij")).isFalse();
        assertThat(Signatures.verifyPayment("order_TEST123456", "pay_TEST123456", expected, "wrong_secret_abcdefgh")).isFalse();
        assertThat(Signatures.verifyPayment("order_TEST123456", "pay_TEST123456", null, "test_secret_abcdefghij")).isFalse();
        assertThat(Signatures.verifyPayment("order_TEST123456", "pay_TEST123456", expected.toUpperCase(), "test_secret_abcdefghij")).isFalse();
    }

    @Test
    void webhookSignatureIsOverRawBytes() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String expected = "84b27e8a6878c004883721f12fdfad7c422cb5d011cf53ec740248305dd242fe";
        assertThat(Signatures.verifyWebhook(body, expected, "webhook_secret_abcdefgh")).isTrue();
        // Re-serialised JSON (whitespace change) must not verify.
        byte[] reformatted = "{ \"event\": \"payment.captured\" }".getBytes(StandardCharsets.UTF_8);
        assertThat(Signatures.verifyWebhook(reformatted, expected, "webhook_secret_abcdefgh")).isFalse();
    }
}
