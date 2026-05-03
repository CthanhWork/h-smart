package com.hsmart.backend.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductRabbitMqConfig {

    public static final String PRODUCT_EXCHANGE = "product.exchange";
    public static final String PRODUCT_SOLD_NOTIFICATION_QUEUE = "product.sold.notification.queue";
    public static final String PRODUCT_SOLD_ROUTING_KEY = "product.event.sold";

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE, true, false);
    }

    @Bean
    public Queue productSoldNotificationQueue() {
        return QueueBuilder.durable(PRODUCT_SOLD_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding productSoldNotificationBinding(Queue productSoldNotificationQueue, TopicExchange productExchange) {
        return BindingBuilder.bind(productSoldNotificationQueue)
                .to(productExchange)
                .with(PRODUCT_SOLD_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.hsmart.backend.application.dto");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
