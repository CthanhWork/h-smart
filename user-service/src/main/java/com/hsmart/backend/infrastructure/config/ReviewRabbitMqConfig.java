package com.hsmart.backend.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.ReviewCreatedEvent;
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
public class ReviewRabbitMqConfig {

    public static final String REVIEW_EXCHANGE = "review.exchange";
    public static final String REVIEW_TRUST_UPDATE_QUEUE = "review.trust.update.queue";
    public static final String REVIEW_CREATED_ROUTING_KEY = "review.event.created";

    @Bean
    public TopicExchange reviewExchange() {
        return new TopicExchange(REVIEW_EXCHANGE, true, false);
    }

    @Bean
    public Queue reviewTrustUpdateQueue() {
        return QueueBuilder.durable(REVIEW_TRUST_UPDATE_QUEUE).build();
    }

    @Bean
    public Binding reviewCreatedTrustUpdateBinding(Queue reviewTrustUpdateQueue, TopicExchange reviewExchange) {
        return BindingBuilder.bind(reviewTrustUpdateQueue)
                .to(reviewExchange)
                .with(REVIEW_CREATED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.hsmart.backend.application.dto", "com.hsmart.review.application.dto");
        typeMapper.setIdClassMapping(Map.of(
                "com.hsmart.review.application.dto.ReviewCreatedEvent", ReviewCreatedEvent.class
        ));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
