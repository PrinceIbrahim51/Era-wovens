package com.aurelia.gateway;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(@NotBlank @Size(min = 32) String internalToken,
                                @NotEmpty List<String> allowedOrigins,
                                boolean secureCookies,
                                @Min(0) int trustedProxyHops) {
    public GatewayProperties {
        if (allowedOrigins != null && allowedOrigins.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalArgumentException("gateway.allowed-origins must list exact origins (no wildcards)");
        }
    }

    @Override
    public String toString() {
        return "GatewayProperties[allowedOrigins=" + allowedOrigins + ", secureCookies=" + secureCookies + "]";
    }
}
