package com.aurelia.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class EdgeFilterTest {
    final EdgeFilter filter = new EdgeFilter(new GatewayProperties("g".repeat(40), List.of("http://localhost:3000"), true, 1));

    ServerWebExchange run(MockServerHttpRequest req, AtomicReference<ServerWebExchange> forwarded) {
        var exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void stripsSpoofedHeadersAndIssuesHttpOnlyCookie() {
        var fwd = new AtomicReference<ServerWebExchange>();
        var ex = run(MockServerHttpRequest.get("/api/v1/cart")
                .header("X-Internal-Token", "attacker").header("X-Guest-Session", "f".repeat(64)).build(), fwd);
        var h = fwd.get().getRequest().getHeaders();
        assertThat(h.getFirst("X-Internal-Token")).isEqualTo("g".repeat(40));
        assertThat(h.getFirst("X-Guest-Session")).matches("^[0-9a-f]{64}$").isNotEqualTo("f".repeat(64));
        var cookie = ex.getResponse().getCookies().getFirst(EdgeFilter.COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(ex.getResponse().getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void existingCookieMapsToStableHashAndIsNotForwarded() {
        String sid = "A".repeat(43);
        var fwd = new AtomicReference<ServerWebExchange>();
        var ex = run(MockServerHttpRequest.get("/api/v1/cart").cookie(new HttpCookie(EdgeFilter.COOKIE, sid)).build(), fwd);
        assertThat(fwd.get().getRequest().getHeaders().getFirst("X-Guest-Session")).isEqualTo(EdgeFilter.sha256(sid));
        assertThat(fwd.get().getRequest().getHeaders().getFirst("Cookie")).isNull();
        assertThat(ex.getResponse().getCookies()).isEmpty();
    }

    @Test
    void crossSiteStateChangeIsRejected() {
        var fwd = new AtomicReference<ServerWebExchange>();
        var ex = run(MockServerHttpRequest.post("/api/v1/cart/items").header("Origin", "https://evil.example").build(), fwd);
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(fwd.get()).isNull();

        var noOrigin = run(MockServerHttpRequest.post("/api/v1/checkout/sessions").build(), fwd);
        assertThat(noOrigin.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void sameOriginStateChangeAndWebhookAreAllowed() {
        var fwd = new AtomicReference<ServerWebExchange>();
        run(MockServerHttpRequest.post("/api/v1/cart/items").header("Origin", "http://localhost:3000").build(), fwd);
        assertThat(fwd.get()).isNotNull();
        fwd.set(null);
        run(MockServerHttpRequest.post("/api/v1/webhooks/razorpay").build(), fwd);
        assertThat(fwd.get()).isNotNull();
    }
}
