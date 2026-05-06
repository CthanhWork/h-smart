package com.hsmart.admin.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.admin.application.dto.ProductCreatedEvent;
import java.util.Map;
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
    public static final String PRODUCT_CREATED_ROUTING_KEY = "product.event.created";
    public static final String PRODUCT_MODERATION_QUEUE = "admin.product.moderation.queue";

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE, true, false);
    }

    @Bean
    public Queue productModerationQueue() {
        return QueueBuilder.durable(PRODUCT_MODERATION_QUEUE).build();
    }

    @Bean
    public Binding productCreatedModerationBinding(Queue productModerationQueue, TopicExchange productExchange) {
        return BindingBuilder.bind(productModerationQueue)
                .to(productExchange)
                .with(PRODUCT_CREATED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.hsmart.backend.application.dto", "com.hsmart.admin.application.dto");
        typeMapper.setIdClassMapping(Map.of(
                "com.hsmart.backend.application.dto.ProductSearchEvent", ProductCreatedEvent.class
        ));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
