package com.hsmart.backend;

import com.hsmart.backend.infrastructure.config.AiServiceProperties;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AiServiceProperties.class, StorageProperties.class, ApplicationProperties.class})
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
