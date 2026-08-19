package com.hsmart.backend.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.OrderCompletedEvent;
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
    public static final String PRODUCT_UPDATED_ROUTING_KEY = "product.event.updated";
    public static final String PRODUCT_SOLD_ROUTING_KEY = "product.event.sold";
    public static final String PRODUCT_DELETED_ROUTING_KEY = "product.event.deleted";
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_PRODUCT_UPDATE_QUEUE = "order.product.update.queue";
    public static final String ORDER_COMPLETED_ROUTING_KEY = "order.event.completed";

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderProductUpdateQueue() {
        return QueueBuilder.durable(ORDER_PRODUCT_UPDATE_QUEUE).build();
    }

    @Bean
    public Binding orderCompletedProductUpdateBinding(Queue orderProductUpdateQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderProductUpdateQueue)
                .to(orderExchange)
                .with(ORDER_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.hsmart.backend.application.dto", "com.hsmart.order.application.dto");
        typeMapper.setIdClassMapping(Map.of(
                "com.hsmart.order.application.dto.OrderCompletedEvent", OrderCompletedEvent.class
        ));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
