package com.aurelia.platform.security;

import com.aurelia.platform.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * The gateway converts the opaque HttpOnly guest cookie into a SHA-256 hex digest and forwards
 * it as {@code X-Guest-Session}. Services never see the raw cookie value and the gateway strips
 * any client-supplied copy of this header.
 */
public final class GuestSession {
    public static final String HEADER = "X-Guest-Session";
    private static final Pattern HEX64 = Pattern.compile("^[0-9a-f]{64}$");

    private GuestSession() {}

    public static String require(HttpServletRequest request) {
        String value = request.getHeader(HEADER);
        if (!isValid(value)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "A browser session is required.");
        }
        return value;
    }

    public static boolean isValid(String value) {
        return value != null && HEX64.matcher(value).matches();
    }
}
