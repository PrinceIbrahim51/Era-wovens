package com.aurelia.catalog.controller;

import com.aurelia.catalog.application.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API (ROLE_SERVICE only; never routed by the gateway). */
@RestController
@RequestMapping("/internal/v1/catalog")
public class InternalCatalogController {
    private final CatalogService service;

    public InternalCatalogController(CatalogService service) {
        this.service = service;
    }

    public record LookupRequest(@NotEmpty @Size(max = 200) List<@Pattern(regexp = "^[A-Z0-9-]{1,40}$") String> skus) {}
    public record LookupResponse(List<CatalogService.SkuInfo> items) {}

    @PostMapping("/skus/lookup")
    public LookupResponse lookup(@Valid @RequestBody LookupRequest req) {
        return new LookupResponse(service.lookupSkus(req.skus().stream().distinct().toList()));
    }
}
