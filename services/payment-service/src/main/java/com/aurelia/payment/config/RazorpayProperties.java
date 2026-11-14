package com.aurelia.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validated at startup and fails closed: only Razorpay Test Mode is permitted in this codebase.
 * Real-money payments require a separate, approved go-live change (see docs/runbooks/go-live.md).
 */
@ConfigurationProperties(prefix = "payments.razorpay")
public record RazorpayProperties(boolean enabled, String mode, String keyId, String keySecret, String webhookSecret,
                                 String apiBaseUrl, String merchantName) {
    public RazorpayProperties {
        if (!"test".equals(mode)) {
            throw new IllegalStateException("payments.razorpay.mode must be 'test'; live payments are not enabled in this build");
        }
        if (enabled) {
            if (keyId == null || !keyId.matches("^rzp_test_[A-Za-z0-9]{8,32}$")) {
                throw new IllegalStateException("RAZORPAY_KEY_ID must be a Razorpay TEST key (rzp_test_...)");
            }
            if (keySecret == null || keySecret.length() < 16) {
                throw new IllegalStateException("RAZORPAY_KEY_SECRET is missing or invalid");
            }
            if (webhookSecret == null || webhookSecret.length() < 16 || webhookSecret.equals(keySecret)) {
                throw new IllegalStateException("RAZORPAY_WEBHOOK_SECRET must be set and differ from the key secret");
            }
            if (apiBaseUrl == null || !(apiBaseUrl.startsWith("https://") || apiBaseUrl.startsWith("http://127.0.0.1")
                    || apiBaseUrl.startsWith("http://localhost"))) {
                throw new IllegalStateException("RAZORPAY_API_BASE_URL must be https");
            }
        }
    }

    @Override
    public String toString() {
        return "RazorpayProperties[enabled=" + enabled + ", mode=" + mode + ", keyId=" + keyId + ", secrets=<redacted>]";
    }
}
