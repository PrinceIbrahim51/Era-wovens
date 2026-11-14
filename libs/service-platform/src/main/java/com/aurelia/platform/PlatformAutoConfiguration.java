package com.aurelia.platform;

import com.aurelia.platform.client.InternalClients;
import com.aurelia.platform.security.InternalTokenAuthenticationFilter;
import com.aurelia.platform.security.PlatformSecurityProperties;
import com.aurelia.platform.web.ApiExceptionHandler;
import com.aurelia.platform.web.RequestIdFilter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.client.RestClient;

/**
 * Default-deny security for every servlet service:
 * <ul>
 *   <li>{@code /internal/**} — sibling services only (ROLE_SERVICE)</li>
 *   <li>{@code /api/**} — via the API gateway (ROLE_GATEWAY) or a sibling service</li>
 *   <li>{@code /actuator/health/**} — liveness/readiness without details</li>
 *   <li>everything else — denied</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(PlatformSecurityProperties.class)
@Import(ApiExceptionHandler.class)
public class PlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, PlatformSecurityProperties props,
                                                    ObjectMapper mapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // no cookies reach services; CSRF is enforced at the gateway
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalTokenAuthenticationFilter(props), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .requestMatchers("/api/**").hasAnyRole("GATEWAY", "SERVICE")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(res, mapper, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(res, mapper, HttpStatus.FORBIDDEN, "FORBIDDEN")));
        return http.build();
    }

    private static void writeProblem(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, String code)
            throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ApiExceptionHandler.problem(status, code, status.getReasonPhrase()));
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        var reg = new FilterRegistrationBean<>(new RequestIdFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictJson() {
        // Strict DTO mapping: unknown fields are rejected (mass-assignment defence).
        return b -> b.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    @ConditionalOnMissingBean
    InternalClients internalClients(RestClient.Builder builder, PlatformSecurityProperties props) {
        return new InternalClients(builder, props);
    }
}
