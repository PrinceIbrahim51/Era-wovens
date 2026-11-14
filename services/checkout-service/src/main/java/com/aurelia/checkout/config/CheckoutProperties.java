package com.aurelia.checkout.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "checkout")
public record CheckoutProperties(
        @NotNull Duration quoteTtl,
        @NotBlank @Size(min = 32) String quoteSigningSecret,
        @Min(60) int reservationTtlSeconds,
        @Min(1) int bulkMinimumUnits,
        boolean bulkAllowMixedSkus,
        @NotNull Tax tax,
        @NotNull Shipping shipping,
        List<String> nonServiceablePincodePrefixes) {

    public record Tax(@Min(0) int lowRateBp, @Min(0) int highRateBp, @Min(1) long thresholdPerPiecePaise) {}
    public record Shipping(@Min(0) long regularFeePaise, @Min(0) long regularFreeFromPaise,
                           @Min(0) long bulkFeePaise, @Min(0) long bulkFreeFromPaise) {}

    public List<String> nonServiceablePincodePrefixes() {
        return nonServiceablePincodePrefixes == null ? List.of()
                : nonServiceablePincodePrefixes.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
