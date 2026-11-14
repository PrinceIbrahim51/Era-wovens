package com.aurelia.inventory.controller;

import com.aurelia.inventory.application.InventoryService;
import com.aurelia.inventory.domain.ReservationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inventory is internal-only: no public route exists in the gateway. */
@Validated
@RestController
@RequestMapping("/internal/v1/inventory")
public class InternalInventoryController {
    private static final String REF = "^[A-Za-z0-9-]{8,64}$";
    private final InventoryService service;

    public InternalInventoryController(InventoryService service) {
        this.service = service;
    }

    public record AvailabilityRequest(@NotEmpty @Size(max = 600) List<@Pattern(regexp = "^[A-Z0-9-]{1,40}$") String> skus) {}
    public record AvailabilityResponse(Map<String, Integer> available) {}
    public record LineDto(@NotNull @Pattern(regexp = "^[A-Z0-9-]{1,40}$") String sku, @Min(1) @Max(10_000) int quantity) {}
    public record ReserveRequest(@NotNull @Pattern(regexp = REF) String reference,
                                 @NotEmpty @Size(max = 100) List<@Valid LineDto> lines,
                                 @Min(60) @Max(7200) Integer ttlSeconds) {}

    @PostMapping("/availability")
    public AvailabilityResponse availability(@Valid @RequestBody AvailabilityRequest req) {
        return new AvailabilityResponse(service.availability(req.skus().stream().distinct().toList()));
    }

    @PostMapping("/reservations")
    public InventoryService.Reservation reserve(@Valid @RequestBody ReserveRequest req) {
        return service.reserve(req.reference(),
                req.lines().stream().map(l -> new InventoryService.Line(l.sku(), l.quantity())).toList(),
                req.ttlSeconds() == null ? null : Duration.ofSeconds(req.ttlSeconds()));
    }

    @GetMapping("/reservations/{reference}")
    public InventoryService.Reservation get(@PathVariable @Pattern(regexp = REF) String reference) {
        return service.find(reference);
    }

    @PostMapping("/reservations/{reference}/commit")
    public InventoryService.Reservation commit(@PathVariable @Pattern(regexp = REF) String reference) {
        return service.commit(reference);
    }

    @PostMapping("/reservations/{reference}/release")
    public InventoryService.Reservation release(@PathVariable @Pattern(regexp = REF) String reference) {
        return service.release(reference, ReservationStatus.RELEASED);
    }
}
