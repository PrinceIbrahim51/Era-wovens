package com.aurelia.payment.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Razorpay HMAC-SHA256 verification with constant-time comparison. */
public final class Signatures {
    private Signatures() {}

    /** Checkout callback: HMAC_SHA256(order_id + "|" + payment_id, key_secret). */
    public static boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId, String signature, String keySecret) {
        if (razorpayOrderId == null || razorpayPaymentId == null || signature == null) return false;
        byte[] expected = hmacHex((razorpayOrderId + "|" + razorpayPaymentId).getBytes(StandardCharsets.UTF_8), keySecret);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Webhook: HMAC_SHA256(raw request body, webhook_secret) in X-Razorpay-Signature. */
    public static boolean verifyWebhook(byte[] rawBody, String signature, String webhookSecret) {
        if (rawBody == null || signature == null) return false;
        return MessageDigest.isEqual(hmacHex(rawBody, webhookSecret), signature.getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacHexString(byte[] data, String secret) {
        return new String(hmacHex(data, secret), StandardCharsets.UTF_8);
    }

    private static byte[] hmacHex(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data)).getBytes(StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
