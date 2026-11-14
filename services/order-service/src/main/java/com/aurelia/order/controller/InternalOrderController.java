package com.aurelia.order.controller;

import com.aurelia.order.application.OrderService;
import com.aurelia.order.domain.OrderStatus;
import com.aurelia.order.infrastructure.OrderRepository.OrderRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
public class InternalOrderController {
    private final OrderService service;

    public InternalOrderController(OrderService service) {
        this.service = service;
    }

    public record ContactDto(@NotBlank @Size(max = 80) String fullName, @NotBlank @Email String email,
                             @Pattern(regexp = "^[6-9][0-9]{9}$") String phone) {}
    public record AddressDto(@NotBlank @Size(max = 120) String line1, @Size(max = 120) String line2,
                             @Size(max = 80) String landmark, @NotBlank @Size(max = 60) String city,
                             @NotBlank @Size(max = 60) String state, @Pattern(regexp = "^[1-9][0-9]{5}$") String pincode) {}
    public record LineDto(@NotBlank String sku, @NotBlank String productName, @NotBlank String colorName,
                          @Min(1) long unitPricePaise, @Min(1) int quantity, @Min(1) long lineTotalPaise,
                          @Min(0) long discountPaise, @Min(0) int taxRateBp, @Min(0) long taxIncludedPaise) {}
    public record CreateDto(@NotBlank @Size(max = 64) String idempotencyKey,
                            @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String ownerHash,
                            @Pattern(regexp = "^(REGULAR|BULK)$") String cartType,
                            @Pattern(regexp = "^(COD|ONLINE)$") String paymentMethod,
                            @NotNull @Valid ContactDto contact, @NotNull @Valid AddressDto shippingAddress,
                            @NotEmpty @Size(max = 100) List<@Valid LineDto> lines, @Min(0) long subtotalPaise,
                            @Min(0) long discountPaise, @Min(0) long shippingPaise, @Min(0) long codFeePaise,
                            @Min(0) long taxIncludedPaise, @Min(1) long totalPaise,
                            @Pattern(regexp = "^INR$") String currency, @Size(max = 20) String couponCode) {}
    public record TransitionDto(@NotNull OrderStatus to, @Size(max = 200) String reason, @NotBlank @Size(max = 60) String actor) {}
    public record CaptureDto(@NotBlank @Pattern(regexp = "^pay_[A-Za-z0-9]{6,40}$") String providerPaymentId,
                             @Min(1) long amountPaise, @Pattern(regexp = "^INR$") String currency) {}
    public record OrderRef(UUID id, String orderNumber, String status) {}
    public record InternalOrder(UUID id, String orderNumber, String ownerHash, String status, String paymentMethod,
                                String paymentStatus, long totalPaise, String currency, String contactName,
                                String contactEmail, String contactPhone, Instant expiresAt) {}

    @PostMapping
    public OrderRef create(@Valid @RequestBody CreateDto d) {
        var c = d.contact();
        var a = d.shippingAddress();
        return ref(service.create(new OrderService.CreateOrder(d.idempotencyKey(), d.ownerHash(), d.cartType(),
                d.paymentMethod(), new OrderService.Contact(c.fullName(), c.email(), c.phone()),
                new OrderService.Address(a.line1(), a.line2(), a.landmark(), a.city(), a.state(), a.pincode()),
                d.lines().stream().map(l -> new OrderService.Line(l.sku(), l.productName(), l.colorName(),
                        l.unitPricePaise(), l.quantity(), l.lineTotalPaise(), l.discountPaise(), l.taxRateBp(),
                        l.taxIncludedPaise())).toList(),
                d.subtotalPaise(), d.discountPaise(), d.shippingPaise(), d.codFeePaise(), d.taxIncludedPaise(),
                d.totalPaise(), d.currency(), d.couponCode())));
    }

    @GetMapping("/{id}")
    public InternalOrder get(@PathVariable UUID id) {
        OrderRow o = service.get(id);
        return new InternalOrder(o.id(), o.orderNumber(), o.ownerHash(), o.status().name(), o.paymentMethod(),
                o.paymentStatus().name(), o.totalPaise(), o.currency(), o.contactName(), o.contactEmail(),
                o.contactPhone(), o.expiresAt());
    }

    @PostMapping("/{id}/transitions")
    public OrderRef transition(@PathVariable UUID id, @Valid @RequestBody TransitionDto d) {
        return ref(service.transition(id, d.to(), d.reason(), d.actor()));
    }

    @PostMapping("/{id}/payment-captured")
    public OrderRef captured(@PathVariable UUID id, @Valid @RequestBody CaptureDto d) {
        return ref(service.paymentCaptured(id, d.providerPaymentId(), d.amountPaise(), d.currency()));
    }

    private static OrderRef ref(OrderRow o) {
        return new OrderRef(o.id(), o.orderNumber(), o.status().name());
    }
}
