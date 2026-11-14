package com.aurelia.platform.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Shared secrets used until workload identity / mTLS replaces them.
 * Both are required and validated at startup (fail closed).
 */
@Validated
@ConfigurationProperties(prefix = "platform.security")
public record PlatformSecurityProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String gatewayToken,
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String serviceToken) {

    public PlatformSecurityProperties {
        if (gatewayToken != null && gatewayToken.equals(serviceToken)) {
            throw new IllegalArgumentException("platform.security.gateway-token and service-token must differ");
        }
    }
}
