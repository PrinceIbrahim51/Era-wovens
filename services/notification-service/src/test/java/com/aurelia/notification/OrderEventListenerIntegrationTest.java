package com.aurelia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aurelia.notification.infrastructure.EmailSender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "platform.security.gateway-token=test-gateway-token-0123456789abcdef",
        "platform.security.service-token=test-service-token-0123456789abcdef"})
@Testcontainers(disabledWithoutDocker = true)
class OrderEventListenerIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired RabbitTemplate template;
    @Autowired JdbcClient jdbc;
    @MockitoBean EmailSender email;

    @Test
    void duplicateDeliveriesSendOneEmail() {
        String id = UUID.randomUUID().toString();
        String body = "{\"orderNumber\":\"ERA-261114-ABCDEF\",\"contactName\":\"Asha\",\"contactEmail\":\"asha@example.com\",\"totalPaise\":154900,\"paymentMethod\":\"COD\"}";
        for (int i = 0; i < 3; i++) {
            var msg = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                    .setMessageId(id).setType("order.confirmed").setContentType("application/json").build();
            template.send("aurelia.orders", "order.confirmed", msg);
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(email, times(1)).send(eq("asha@example.com"), contains("ERA-261114-ABCDEF"), anyString()));
        Integer logged = jdbc.sql("SELECT count(*) FROM notification_log WHERE message_id = :id").param("id", id)
                .query(Integer.class).single();
        assertThat(logged).isEqualTo(1);
    }
}
