package com.aurelia.inventory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory.reservation")
public record ReservationProperties(Duration defaultTtl, Duration maxTtl, Duration expirySweepInterval) {}
