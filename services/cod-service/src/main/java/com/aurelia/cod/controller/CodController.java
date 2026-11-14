package com.aurelia.cod.controller;

import com.aurelia.cod.application.CodService;
import com.aurelia.cod.domain.CodRules;
import com.aurelia.platform.security.GuestSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodController {
    private static final String PIN = "^[1-9][0-9]{5}$";
    private static final String TYPE = "^(REGULAR|BULK)$";
    private static final String OWNER = "^[0-9a-f]{64}$";
    private static final String PHONE = "^[6-9][0-9]{9}$";
    private final CodService service;

    public CodController(CodService service) {
        this.service = service;
    }

    public record PublicEligibility(@NotNull @Pattern(regexp = PIN) String pincode, @NotNull @Pattern(regexp = TYPE) String cartType) {}
    public record InternalEligibility(@Pattern(regexp = OWNER) String ownerHash, @NotNull @Pattern(regexp = PIN) String pincode,
                                      @Min(1) long orderValuePaise, @NotNull @Pattern(regexp = TYPE) String cartType,
                                      @Pattern(regexp = PHONE) String phone) {}
    public record ConfirmRequest(@NotNull UUID orderId, @NotNull @Pattern(regexp = OWNER) String ownerHash,
                                 @NotNull @Pattern(regexp = PIN) String pincode, @Min(1) long orderValuePaise,
                                 @Min(0) long feePaise, @NotNull @Pattern(regexp = TYPE) String cartType,
                                 @NotNull @Pattern(regexp = PHONE) String phone) {}
    public record OutcomeRequest(@NotNull @Pattern(regexp = "^(COLLECTED|RTO|CANCELLED)$") String outcome,
                                 @Min(0) long collectedPaise, @NotBlank @Size(max = 80) String actor,
                                 @Size(max = 300) String note) {}

    /** Advisory, read-only check for the storefront. Checkout re-evaluates with server-side amounts. */
    @PostMapping("/api/v1/cod/eligibility")
    public CodRules.Decision publicEligibility(HttpServletRequest req, @Valid @RequestBody PublicEligibility body) {
        return service.eligibility(GuestSession.require(req), body.pincode(), null, body.cartType(), null);
    }

    @PostMapping("/internal/v1/cod/eligibility")
    public CodRules.Decision eligibility(@Valid @RequestBody InternalEligibility body) {
        return service.eligibility(body.ownerHash(), body.pincode(), body.orderValuePaise(), body.cartType(), body.phone());
    }

    @PostMapping("/internal/v1/cod/confirmations")
    public CodService.Confirmation confirm(@Valid @RequestBody ConfirmRequest b) {
        return service.confirm(b.orderId(), b.ownerHash(), b.pincode(), b.orderValuePaise(), b.feePaise(), b.cartType(), b.phone());
    }

    /** Courier/staff reconciliation hook. Will be exposed to admin-service (with RBAC + audit) in M4. */
    @PostMapping("/internal/v1/cod/confirmations/{orderId}/outcome")
    public CodService.Confirmation outcome(@PathVariable UUID orderId, @Valid @RequestBody OutcomeRequest b) {
        return service.recordOutcome(orderId, b.outcome(), b.collectedPaise(), b.actor(), b.note());
    }
}
