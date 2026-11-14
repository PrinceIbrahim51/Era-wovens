package com.aurelia.checkout.controller;

import com.aurelia.checkout.application.CheckoutService;
import com.aurelia.checkout.domain.CartType;
import com.aurelia.checkout.domain.PaymentMethod;
import com.aurelia.platform.security.GuestSession;
import com.aurelia.platform.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    static final Set<String> STATES = Set.of("Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh",
            "Assam", "Bihar", "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa",
            "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala", "Ladakh",
            "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha",
            "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
            "Uttarakhand", "West Bengal");
    private static final String TEXT = "^[\\p{L}\\p{N} .,'#/()&:-]*$";

    private final CheckoutService service;

    public CheckoutController(CheckoutService service) {
        this.service = service;
    }

    public record QuoteRequest(@NotNull CartType cartType,
                               @NotNull @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code") String pincode,
                               @Size(max = 20) String couponCode) {}

    public record ContactDto(@NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[\\p{L} .'-]+$") String fullName,
                             @NotBlank @Email @Size(max = 254) String email,
                             @NotNull @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number") String phone) {}

    public record AddressDto(@NotBlank @Size(max = 120) @Pattern(regexp = TEXT) String line1,
                             @Size(max = 120) @Pattern(regexp = TEXT) String line2,
                             @Size(max = 80) @Pattern(regexp = TEXT) String landmark,
                             @NotBlank @Size(max = 60) @Pattern(regexp = "^[\\p{L} .'-]+$") String city,
                             @NotBlank String state,
                             @NotNull @Pattern(regexp = "^[1-9][0-9]{5}$") String pincode) {}

    public record SessionRequestDto(@NotNull UUID quoteId, @NotNull PaymentMethod paymentMethod,
                                    @NotNull @Valid ContactDto contact, @NotNull @Valid AddressDto shippingAddress) {}

    @PostMapping("/quotes")
    public CheckoutService.Quote quote(HttpServletRequest http, @Valid @RequestBody QuoteRequest req) {
        return service.createQuote(GuestSession.require(http), req.cartType(), req.pincode(), req.couponCode());
    }

    @PostMapping("/sessions")
    public ResponseEntity<CheckoutService.SessionResponse> session(
            HttpServletRequest http,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SessionRequestDto req) {
        if (!idempotencyKey.matches("^[A-Za-z0-9-]{16,64}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 16-64 characters.");
        }
        if (!STATES.contains(req.shippingAddress().state())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Choose a valid state or union territory.");
        }
        var c = req.contact();
        var a = req.shippingAddress();
        var res = service.placeOrder(GuestSession.require(http), idempotencyKey, new CheckoutService.SessionRequest(
                req.quoteId(), req.paymentMethod(),
                new CheckoutService.Contact(c.fullName().strip(), c.email().strip().toLowerCase(), c.phone()),
                new CheckoutService.Address(a.line1().strip(), blankToNull(a.line2()), blankToNull(a.landmark()),
                        a.city().strip(), a.state(), a.pincode())));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
