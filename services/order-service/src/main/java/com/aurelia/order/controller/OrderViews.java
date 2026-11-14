package com.aurelia.order.controller;

import com.aurelia.order.application.OrderService;
import com.aurelia.order.domain.OrderStatus;
import com.aurelia.order.infrastructure.OrderRepository;
import com.aurelia.order.infrastructure.OrderRepository.OrderRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderViews {
    private final OrderRepository repo;
    private final ObjectMapper mapper;

    public OrderViews(OrderRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public record Totals(long subtotalPaise, long discountPaise, long shippingPaise, long codFeePaise,
                         long taxIncludedPaise, long totalPaise, String currency) {}
    public record TimelineEntry(String status, Instant at, String note) {}
    public record OrderView(UUID id, String orderNumber, String status, String paymentMethod, String paymentStatus,
                            String cartType, Instant createdAt, Instant paymentDueBy,
                            List<OrderRepository.ItemRow> items, Totals totals, String couponCode,
                            String contactName, OrderService.Address shippingAddress, List<TimelineEntry> timeline) {}
    public record OrderSummary(UUID id, String orderNumber, String status, String paymentMethod, Instant createdAt,
                               long totalPaise, int units) {}

    /** @param masked true for order lookups by number+email: street lines are hidden. */
    public OrderView view(OrderRow o, boolean masked) {
        OrderService.Address a;
        try {
            a = mapper.readValue(o.shippingAddressJson(), OrderService.Address.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (masked) a = new OrderService.Address("••••", null, null, a.city(), a.state(), a.pincode());
        var timeline = repo.events(o.id()).stream()
                .filter(e -> e.fromStatus() == null || !e.fromStatus().equals(e.toStatus()))
                .map(e -> new TimelineEntry(e.toStatus(), e.at(), publicNote(e.toStatus()))).toList();
        return new OrderView(o.id(), o.orderNumber(), o.status().name(), o.paymentMethod(), o.paymentStatus().name(),
                o.cartType(), o.createdAt(), o.status() == OrderStatus.PENDING_PAYMENT ? o.expiresAt() : null,
                repo.items(o.id()), new Totals(o.subtotalPaise(), o.discountPaise(), o.shippingPaise(), o.codFeePaise(),
                o.taxIncludedPaise(), o.totalPaise(), o.currency()), o.couponCode(),
                masked ? firstName(o.contactName()) : o.contactName(), a, timeline);
    }

    public OrderSummary summary(OrderRow o) {
        int units = repo.items(o.id()).stream().mapToInt(OrderRepository.ItemRow::quantity).sum();
        return new OrderSummary(o.id(), o.orderNumber(), o.status().name(), o.paymentMethod(), o.createdAt(),
                o.totalPaise(), units);
    }

    private static String firstName(String name) {
        int sp = name.indexOf(' ');
        return sp > 0 ? name.substring(0, sp) : name;
    }

    private static String publicNote(String status) {
        return switch (status) {
            case "DRAFT" -> "Order created";
            case "PENDING_PAYMENT" -> "Awaiting payment";
            case "COD_CONFIRMED" -> "Cash on delivery accepted";
            case "CONFIRMED" -> "Order confirmed";
            case "FULFILLING" -> "Being packed";
            case "SHIPPED" -> "Shipped";
            case "DELIVERED" -> "Delivered";
            case "REVIEW_REQUIRED" -> "Our team is reviewing this order";
            case "CANCELLED" -> "Cancelled";
            case "EXPIRED" -> "Payment window expired";
            case "RETURNED" -> "Returned";
            case "REFUNDED" -> "Refunded";
            default -> status;
        };
    }
}
