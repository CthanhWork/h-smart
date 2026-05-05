package com.hsmart.search.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.search.application.dto.ProductSearchEvent;
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
    public static final String PRODUCT_SEARCH_INDEX_QUEUE = "product.search.index.queue";
    public static final String PRODUCT_CREATED_ROUTING_KEY = "product.event.created";
    public static final String PRODUCT_UPDATED_ROUTING_KEY = "product.event.updated";

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE, true, false);
    }

    @Bean
    public Queue productSearchIndexQueue() {
        return QueueBuilder.durable(PRODUCT_SEARCH_INDEX_QUEUE).build();
    }

    @Bean
    public Binding productCreatedSearchBinding(Queue productSearchIndexQueue, TopicExchange productExchange) {
        return BindingBuilder.bind(productSearchIndexQueue)
                .to(productExchange)
                .with(PRODUCT_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding productUpdatedSearchBinding(Queue productSearchIndexQueue, TopicExchange productExchange) {
        return BindingBuilder.bind(productSearchIndexQueue)
                .to(productExchange)
                .with(PRODUCT_UPDATED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.hsmart.backend.application.dto", "com.hsmart.search.application.dto");
        typeMapper.setIdClassMapping(Map.of(
                "com.hsmart.backend.application.dto.ProductSearchEvent", ProductSearchEvent.class
        ));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
