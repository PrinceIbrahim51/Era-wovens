package com.aurelia.notification.application;

import com.aurelia.notification.config.MessagingConfig;
import com.aurelia.notification.infrastructure.EmailSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Inbox pattern: each broker message id is processed at most once even with redelivery. */
@Component
public class OrderEventListener {
    private final JdbcClient jdbc;
    private final EmailSender email;
    private final ObjectMapper mapper;

    public OrderEventListener(JdbcClient jdbc, EmailSender email, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.email = email;
        this.mapper = mapper;
    }

    @Transactional
    @RabbitListener(queues = MessagingConfig.QUEUE)
    public void onMessage(Message message) {
        String id = message.getMessageProperties().getMessageId();
        String type = message.getMessageProperties().getType();
        if (id == null || type == null) throw new AmqpRejectAndDontRequeueException("missing id/type");
        int fresh = jdbc.sql("INSERT INTO processed_messages (message_id, event_type) VALUES (:id, :t) ON CONFLICT DO NOTHING")
                .param("id", id).param("t", type).update();
        if (fresh == 0) return; // duplicate delivery

        JsonNode e;
        try {
            e = mapper.readTree(message.getBody());
        } catch (IOException ex) {
            throw new AmqpRejectAndDontRequeueException("malformed payload", ex);
        }
        String to = e.path("contactEmail").asText();
        String number = e.path("orderNumber").asText();
        String name = e.path("contactName").asText("there");
        String total = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
                .format(e.path("totalPaise").asLong() / 100.0);
        var tpl = switch (type) {
            case "order.confirmed" -> new String[]{"order-confirmed", "Your Era Wovens order " + number + " is confirmed",
                    "Hi " + name + ", thank you! Order " + number + " (" + total + ", "
                            + ("COD".equals(e.path("paymentMethod").asText()) ? "cash on delivery" : "paid online — test mode")
                            + ") is confirmed. We'll email you when it ships."};
            case "order.cancelled" -> new String[]{"order-cancelled", "Order " + number + " was cancelled",
                    "Hi " + name + ", order " + number + " has been cancelled. Any payment will be refunded."};
            case "order.expired" -> new String[]{"order-expired", "Payment for order " + number + " was not completed",
                    "Hi " + name + ", we didn't receive payment for order " + number + ", so it has been released."};
            default -> null;
        };
        if (tpl == null || to.isBlank()) return;
        email.send(to, tpl[1], tpl[2]);
        jdbc.sql("INSERT INTO notification_log (message_id, channel, template, recipient_hash, status) VALUES (:id, 'EMAIL', :t, :h, 'SENT')")
                .param("id", id).param("t", tpl[0]).param("h", sha256(to.toLowerCase())).update();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
