package com.aurelia.checkout.domain;

import java.util.List;
import java.util.regex.Pattern;

public final class Pincodes {
    private static final Pattern PIN = Pattern.compile("^[1-9][0-9]{5}$");
    private Pincodes() {}

    public static boolean isValid(String pin) {
        return pin != null && PIN.matcher(pin).matches();
    }

    public static boolean isServiceable(String pin, List<String> blockedPrefixes) {
        return isValid(pin) && blockedPrefixes.stream().noneMatch(pin::startsWith);
    }
}
