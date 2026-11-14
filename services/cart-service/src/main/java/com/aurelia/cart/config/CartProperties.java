package com.aurelia.cart.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cart")
public record CartProperties(Duration ttl, @Min(1) int maxLines, Bulk bulk) {
    public record Bulk(@Min(1) int minimumUnits, boolean allowMixedSkus, @Min(1) int maxLineQuantity) {}
}
