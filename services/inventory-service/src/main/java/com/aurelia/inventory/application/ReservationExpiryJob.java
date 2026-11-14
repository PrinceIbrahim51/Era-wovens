package com.aurelia.inventory.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);
    private final InventoryService service;

    public ReservationExpiryJob(InventoryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${inventory.reservation.expiry-sweep-interval}")
    public void sweep() {
        try {
            while (service.expireDue(100) == 100) {
                // keep draining while full batches are found
            }
        } catch (RuntimeException e) {
            log.warn("Reservation expiry sweep failed: {}", e.getMessage());
        }
    }
}
