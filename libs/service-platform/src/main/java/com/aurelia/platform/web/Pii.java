package com.aurelia.platform.web;

/** Helpers for keeping personal data out of logs. */
public final class Pii {
    private Pii() {}

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        return (at > 0 ? email.charAt(0) : '*') + "***" + email.substring(at);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "******" + phone.substring(phone.length() - 2);
    }
}
