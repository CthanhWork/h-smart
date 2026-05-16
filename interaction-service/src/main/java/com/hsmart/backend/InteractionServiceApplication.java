package com.hsmart.backend;

import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.infrastructure.config.IntentClassifierProperties;
import com.hsmart.backend.infrastructure.config.OrderServiceProperties;
import com.hsmart.backend.infrastructure.config.PolicySearchProperties;
import com.hsmart.backend.infrastructure.config.ProductServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AssistantProperties.class,
        IntentClassifierProperties.class,
        OrderServiceProperties.class,
        PolicySearchProperties.class,
        ProductServiceProperties.class
})
public class InteractionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InteractionServiceApplication.class, args);
    }
}
