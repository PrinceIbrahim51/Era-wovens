package com.aurelia.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {
    public static final String QUEUE = "notification.order-events";
    public static final String DLX = "aurelia.orders.dlx";
    public static final String DLQ = QUEUE + ".dlq";

    @Bean TopicExchange ordersExchange() { return new TopicExchange("aurelia.orders", true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean
    Queue orderEvents() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();
    }

    @Bean Queue orderEventsDlq() { return QueueBuilder.durable(DLQ).build(); }

    @Bean Binding orderEventsBinding() { return BindingBuilder.bind(orderEvents()).to(ordersExchange()).with("order.*"); }
    @Bean Binding dlqBinding() { return BindingBuilder.bind(orderEventsDlq()).to(deadLetterExchange()).with(DLQ); }
}
