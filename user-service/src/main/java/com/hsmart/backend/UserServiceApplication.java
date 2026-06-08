package com.hsmart.backend;

import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.config.JwtProperties;
import com.hsmart.backend.infrastructure.config.MailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AccountLifecycleProperties.class,
        JwtProperties.class,
        MailProperties.class
})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
