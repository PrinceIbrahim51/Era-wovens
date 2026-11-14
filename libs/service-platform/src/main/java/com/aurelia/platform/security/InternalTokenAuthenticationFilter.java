package com.aurelia.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates callers that present a shared internal token. The API gateway gets ROLE_GATEWAY
 * (public routes only); sibling services get ROLE_SERVICE (internal routes).
 */
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] gatewayToken;
    private final byte[] serviceToken;

    public InternalTokenAuthenticationFilter(PlatformSecurityProperties props) {
        this.gatewayToken = props.gatewayToken().getBytes(StandardCharsets.UTF_8);
        this.serviceToken = props.serviceToken().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null) {
            byte[] bytes = presented.getBytes(StandardCharsets.UTF_8);
            String role = null;
            if (MessageDigest.isEqual(bytes, serviceToken)) {
                role = "ROLE_SERVICE";
            } else if (MessageDigest.isEqual(bytes, gatewayToken)) {
                role = "ROLE_GATEWAY";
            }
            if (role != null) {
                var auth = new UsernamePasswordAuthenticationToken(
                        role.substring(5).toLowerCase(), null, List.of(new SimpleGrantedAuthority(role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
