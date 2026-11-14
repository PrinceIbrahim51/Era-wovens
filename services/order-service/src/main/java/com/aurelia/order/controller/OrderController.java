package com.aurelia.order.controller;

import com.aurelia.order.application.OrderService;
import com.aurelia.order.infrastructure.OrderRepository;
import com.aurelia.platform.security.GuestSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Customer-facing reads. Ownership is enforced here; non-owned orders look identical to missing ones. */
@RestController
public class OrderController {
    private final OrderService service;
    private final OrderRepository repo;
    private final OrderViews views;

    public OrderController(OrderService service, OrderRepository repo, OrderViews views) {
        this.service = service;
        this.repo = repo;
        this.views = views;
    }

    public record LookupRequest(@NotBlank @Pattern(regexp = "^ERA-[0-9]{6}-[A-Z0-9]{6}$") String orderNumber,
                                @NotBlank @Email @Size(max = 254) String email) {}

    @GetMapping("/api/v1/orders/{id}")
    public OrderViews.OrderView get(HttpServletRequest req, @PathVariable UUID id) {
        String owner = GuestSession.require(req);
        var o = repo.byId(id).filter(x -> equal(x.ownerHash(), owner)).orElseThrow(OrderService::notFound);
        return views.view(o, false);
    }

    @GetMapping("/api/v1/me/orders")
    public List<OrderViews.OrderSummary> mine(HttpServletRequest req) {
        return repo.byOwner(GuestSession.require(req), 50).stream().map(views::summary).toList();
    }

    @PostMapping("/api/v1/orders/lookup")
    public OrderViews.OrderView lookup(@Valid @RequestBody LookupRequest body) {
        var o = repo.byNumber(body.orderNumber().strip().toUpperCase())
                .filter(x -> equal(x.contactEmail(), body.email().strip().toLowerCase()))
                .orElseThrow(OrderService::notFound);
        return views.view(o, true);
    }

    private static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
