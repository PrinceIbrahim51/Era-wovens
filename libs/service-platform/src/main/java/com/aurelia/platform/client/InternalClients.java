package com.aurelia.platform.client;

import com.aurelia.platform.security.InternalTokenAuthenticationFilter;
import com.aurelia.platform.security.PlatformSecurityProperties;
import com.aurelia.platform.web.RequestIdFilter;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

/** Builds RestClients for service-to-service calls carrying the service token and request id. */
public class InternalClients {
    private final RestClient.Builder builder;
    private final PlatformSecurityProperties props;

    public InternalClients(RestClient.Builder builder, PlatformSecurityProperties props) {
        this.builder = builder;
        this.props = props;
    }

    public RestClient create(String baseUrl) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder.detect().build(settings))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(InternalTokenAuthenticationFilter.HEADER, props.serviceToken());
                    String rid = MDC.get(RequestIdFilter.MDC_KEY);
                    if (rid != null) request.getHeaders().set(RequestIdFilter.HEADER, rid);
                    return execution.execute(request, body);
                })
                .build();
    }
}
