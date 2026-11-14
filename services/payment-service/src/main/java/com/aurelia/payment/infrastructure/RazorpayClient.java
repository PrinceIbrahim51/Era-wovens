package com.aurelia.payment.infrastructure;

import com.aurelia.payment.config.RazorpayProperties;
import com.aurelia.platform.web.UpstreamException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Minimal server-side Razorpay REST client (Orders + Payments fetch). Secrets never leave this service. */
@Component
public class RazorpayClient {
    private final RestClient client;

    public RazorpayClient(RestClient.Builder builder, RazorpayProperties props) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3)).withReadTimeout(Duration.ofSeconds(10));
        String basic = props.enabled()
                ? Base64.getEncoder().encodeToString((props.keyId() + ":" + props.keySecret()).getBytes(StandardCharsets.UTF_8))
                : "";
        this.client = builder.clone().baseUrl(props.apiBaseUrl() == null ? "https://api.razorpay.com" : props.apiBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderOrder(String id, long amount, String currency, String status, String receipt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderPayment(String id, String order_id, long amount, String currency, String status,
                                  String method, Boolean captured, String error_code, String error_description) {}

    record CreateOrder(long amount, String currency, String receipt, Map<String, String> notes) {}

    public ProviderOrder createOrder(long amountPaise, String currency, String receipt, String internalOrderId) {
        try {
            return client.post().uri("/v1/orders")
                    .body(new CreateOrder(amountPaise, currency, receipt, Map.of("aurelia_order_id", internalOrderId)))
                    .retrieve().body(ProviderOrder.class);
        } catch (RestClientException e) {
            throw new UpstreamException("razorpay create order failed", e);
        }
    }

    public ProviderPayment fetchPayment(String paymentId) {
        try {
            return client.get().uri("/v1/payments/{id}", paymentId).retrieve().body(ProviderPayment.class);
        } catch (RestClientException e) {
            throw new UpstreamException("razorpay fetch payment failed", e);
        }
    }
}
