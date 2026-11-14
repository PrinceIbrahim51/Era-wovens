package com.aurelia.order.infrastructure;

import com.aurelia.order.config.MessagingConfig;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional outbox relay: events are written in the same DB transaction as the state change and
 * published here with broker confirms. Delivery is at-least-once; consumers dedupe by message id.
 */
@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;

    public OutboxPublisher(JdbcClient jdbc, RabbitTemplate rabbit, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.tx = tx;
    }

    record Pending(UUID id, String type, String payload) {}

    @Scheduled(fixedDelayString = "${orders.outbox-interval}")
    public void publish() {
        tx.executeWithoutResult(status -> {
            List<Pending> batch = jdbc.sql("""
                    SELECT id, event_type, payload::text FROM outbox WHERE published_at IS NULL AND attempts < 20
                    ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED
                    """).query((rs, i) -> new Pending(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3))).list();
            for (Pending p : batch) {
                try {
                    var props = new MessageProperties();
                    props.setMessageId(p.id().toString());
                    props.setType(p.type());
                    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    var message = MessageBuilder.withBody(p.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .andProperties(props).build();
                    rabbit.invoke(ops -> {
                        ops.send(MessagingConfig.EXCHANGE, p.type(), message);
                        ops.waitForConfirmsOrDie(5_000);
                        return null;
                    });
                    jdbc.sql("UPDATE outbox SET published_at = now(), attempts = attempts + 1 WHERE id = :id")
                            .param("id", p.id()).update();
                } catch (RuntimeException e) {
                    log.warn("Outbox publish failed for {}: {}", p.id(), e.getMessage());
                    String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    jdbc.sql("UPDATE outbox SET attempts = attempts + 1, last_error = :e WHERE id = :id")
                            .param("e", err.substring(0, Math.min(300, err.length()))).param("id", p.id()).update();
                    break; // broker likely down; retry on next tick
                }
            }
        });
    }
}
