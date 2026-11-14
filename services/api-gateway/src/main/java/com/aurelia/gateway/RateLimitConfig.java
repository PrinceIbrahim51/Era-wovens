package com.aurelia.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
    /**
     * Client IP, trusting only the configured number of proxy hops in X-Forwarded-For
     * (the storefront's server-side proxy), so clients cannot spoof their address.
     */
    @Bean
    KeyResolver clientIpKeyResolver(GatewayProperties props) {
        var resolver = props.trustedProxyHops() > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(props.trustedProxyHops())
                : null;
        return exchange -> {
            var addr = resolver != null ? resolver.resolve(exchange) : exchange.getRequest().getRemoteAddress();
            return Mono.just(addr == null || addr.getAddress() == null ? "unknown" : addr.getAddress().getHostAddress());
        };
    }
}
