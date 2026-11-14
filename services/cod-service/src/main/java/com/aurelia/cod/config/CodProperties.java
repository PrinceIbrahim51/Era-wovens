package com.aurelia.cod.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cod")
public record CodProperties(@Min(0) long feePaise, @Min(1) long maxOrderValuePaise, boolean allowBulk,
                            @Min(1) int maxPendingOrders, @Min(0) int maxRecentRto, Duration rtoLookback,
                            List<String> blockedPincodePrefixes, @NotBlank @Size(min = 32) String phonePepper) {
    public List<String> blockedPincodePrefixes() {
        return blockedPincodePrefixes == null ? List.of()
                : blockedPincodePrefixes.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
