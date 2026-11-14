package com.aurelia.platform.client;

import com.aurelia.platform.web.ApiException;
import com.aurelia.platform.web.UpstreamException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Wraps a sibling-service call. Business 4xx problems (e.g. INSUFFICIENT_STOCK) are re-thrown as
 * {@link ApiException} with the upstream code; auth failures, 5xx and I/O errors become
 * {@link UpstreamException} (503) so misconfiguration never leaks details to clients.
 */
public final class Upstream {
    private static final Set<String> PASS_THROUGH_FIELDS = Set.of("skus", "status", "maxQuantity", "reasons");

    private Upstream() {}

    public static <T> T call(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == null || status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN
                    || status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new UpstreamException(what + " rejected: " + e.getStatusCode(), e);
            }
            Map<?, ?> body = null;
            try {
                body = e.getResponseBodyAs(Map.class);
            } catch (RuntimeException ignored) {
                // non-JSON body
            }
            String code = body != null && body.get("code") instanceof String s ? s : "UPSTREAM_REJECTED";
            String detail = body != null && body.get("detail") instanceof String s ? s : "Request rejected.";
            Map<String, Object> details = new HashMap<>();
            if (body != null) {
                body.forEach((k, v) -> {
                    if (PASS_THROUGH_FIELDS.contains(String.valueOf(k))) details.put(String.valueOf(k), v);
                });
            }
            throw new ApiException(status, code, detail, details);
        } catch (RestClientException e) {
            throw new UpstreamException(what + " failed", e);
        }
    }

    public static void run(String what, Runnable call) {
        call(what, () -> {
            call.run();
            return null;
        });
    }
}
