package com.aurelia.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);
    private final OrderService service;

    public OrderExpiryJob(OrderService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${orders.expiry-interval}")
    public void run() {
        try {
            service.expireDue();
        } catch (RuntimeException e) {
            log.warn("Order expiry sweep failed: {}", e.getMessage());
        }
    }
}
