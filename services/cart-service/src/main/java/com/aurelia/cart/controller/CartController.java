package com.aurelia.cart.controller;

import com.aurelia.cart.application.CartService;
import com.aurelia.cart.domain.CartType;
import com.aurelia.platform.security.GuestSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    public record AddItem(@NotNull @Pattern(regexp = "^[A-Z0-9-]{1,40}$") String sku,
                          @Min(1) @Max(500) int quantity, CartType cartType) {}
    public record UpdateItem(@Min(1) @Max(500) int quantity) {}

    @GetMapping
    public CartService.CartView get(HttpServletRequest req, @RequestParam(defaultValue = "REGULAR") CartType type) {
        return service.view(GuestSession.require(req), type);
    }

    @GetMapping("/summary")
    public CartService.Summary summary(HttpServletRequest req) {
        return service.summary(GuestSession.require(req));
    }

    @PostMapping("/items")
    public CartService.CartView add(HttpServletRequest req, @Valid @RequestBody AddItem body) {
        return service.add(GuestSession.require(req), body.cartType() == null ? CartType.REGULAR : body.cartType(),
                body.sku(), body.quantity());
    }

    @PatchMapping("/items/{itemId}")
    public CartService.CartView update(HttpServletRequest req, @PathVariable UUID itemId, @Valid @RequestBody UpdateItem body) {
        return service.update(GuestSession.require(req), itemId, body.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartService.CartView remove(HttpServletRequest req, @PathVariable UUID itemId) {
        return service.remove(GuestSession.require(req), itemId);
    }
}
