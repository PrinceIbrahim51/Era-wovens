package com.aurelia.gateway;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Edge controls applied to every routed request:
 * <ol>
 *   <li>strip spoofable internal headers sent by clients;</li>
 *   <li>request ID propagation;</li>
 *   <li>CSRF defence for cookie-authenticated state changes (exact Origin/Referer allowlist);</li>
 *   <li>opaque HttpOnly guest-session cookie, forwarded downstream only as a SHA-256 digest;</li>
 *   <li>gateway credential for downstream services and security response headers.</li>
 * </ol>
 */
@Component
public class EdgeFilter implements GlobalFilter, Ordered {
    public static final String COOKIE = "aurelia_sid";
    static final String INTERNAL = "X-Internal-Token";
    static final String GUEST = "X-Guest-Session";
    static final String REQUEST_ID = "X-Request-Id";
    private static final Pattern SID = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Pattern RID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final Set<HttpMethod> SAFE = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
    private static final Set<String> CSRF_EXEMPT = Set.of("/api/v1/webhooks/razorpay"); // HMAC-authenticated

    private final GatewayProperties props;
    private final SecureRandom random = new SecureRandom();

    public EdgeFilter(GatewayProperties props) {
        this.props = props;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        ServerHttpResponse res = exchange.getResponse();
        String requestId = req.getHeaders().getFirst(REQUEST_ID);
        if (requestId == null || !RID.matcher(requestId).matches()) requestId = UUID.randomUUID().toString();
        securityHeaders(res.getHeaders(), req.getPath().value(), requestId);

        String path = req.getPath().value();
        if (!SAFE.contains(req.getMethod()) && !CSRF_EXEMPT.contains(path) && !originAllowed(req)) {
            return reject(res, HttpStatus.FORBIDDEN, "CSRF_ORIGIN_REJECTED", "Cross-site request blocked.");
        }

        HttpCookie cookie = req.getCookies().getFirst(COOKIE);
        String sid = cookie != null && SID.matcher(cookie.getValue()).matches() ? cookie.getValue() : null;
        if (sid == null) {
            sid = newSessionId();
            res.addCookie(ResponseCookie.from(COOKIE, sid).httpOnly(true).secure(props.secureCookies())
                    .sameSite("Lax").path("/").maxAge(Duration.ofDays(30)).build());
        }
        String guestHash = sha256(sid);
        String rid = requestId;
        ServerHttpRequest mutated = req.mutate().headers(h -> {
            h.remove(INTERNAL);
            h.remove(GUEST);
            h.remove(HttpHeaders.COOKIE); // services never receive browser cookies
            h.set(INTERNAL, props.internalToken());
            h.set(GUEST, guestHash);
            h.set(REQUEST_ID, rid);
        }).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    boolean originAllowed(ServerHttpRequest req) {
        String origin = req.getHeaders().getOrigin();
        if (origin == null) {
            String referer = req.getHeaders().getFirst(HttpHeaders.REFERER);
            if (referer == null) return false;
            try {
                URI u = URI.create(referer);
                origin = u.getScheme() + "://" + u.getRawAuthority();
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return props.allowedOrigins().contains(origin);
    }

    private void securityHeaders(HttpHeaders h, String path, String requestId) {
        h.set(REQUEST_ID, requestId);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "strict-origin-when-cross-origin");
        h.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        h.set("Cross-Origin-Resource-Policy", "same-site");
        if (props.secureCookies()) h.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        if (!path.startsWith("/api/v1/catalog/")) h.setCacheControl("no-store");
    }

    private String newSessionId() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Mono<Void> reject(ServerHttpResponse res, HttpStatus status, String code, String detail) {
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = ("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase() + "\",\"code\":\""
                + code + "\",\"detail\":\"" + detail + "\"}").getBytes(StandardCharsets.UTF_8);
        return res.writeWith(Mono.just(res.bufferFactory().wrap(body)));
    }
}
