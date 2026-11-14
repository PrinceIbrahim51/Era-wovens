package com.aurelia.cart.controller;

import com.aurelia.cart.application.CartService;
import com.aurelia.cart.domain.CartType;
import com.aurelia.platform.security.GuestSession;
import com.aurelia.platform.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/carts/{ownerHash}")
public class InternalCartController {
    private final CartService service;

    public InternalCartController(CartService service) {
        this.service = service;
    }

    public record Line(@NotNull String sku, @Min(1) @Max(10_000) int quantity) {}
    public record ConsumeRequest(@NotNull CartType cartType, @NotEmpty @Size(max = 100) List<@Valid Line> lines) {}

    @GetMapping
    public CartService.RawCart get(@PathVariable String ownerHash, @RequestParam CartType type) {
        return service.raw(owner(ownerHash), type);
    }

    @PostMapping("/consume")
    public CartService.RawCart consume(@PathVariable String ownerHash, @Valid @RequestBody ConsumeRequest req) {
        return service.consume(owner(ownerHash), req.cartType(),
                req.lines().stream().map(l -> new CartService.RawLine(l.sku(), l.quantity())).toList());
    }

    private static String owner(String hash) {
        if (!GuestSession.isValid(hash)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OWNER", "Invalid owner.");
        return hash;
    }
}
