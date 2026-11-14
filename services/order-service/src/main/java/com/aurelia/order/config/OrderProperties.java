package com.aurelia.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orders")
public record OrderProperties(Duration paymentWindow, Duration draftTtl, Duration outboxInterval, Duration expiryInterval) {}
