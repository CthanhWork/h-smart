package com.hsmart.backend.infrastructure.config;

import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

class ExternalRestClientConfigurationTest {

    @Test
    void externalRestClientsShouldNotDependOnLoadBalancedBuilders() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    "productLoadBalancedRestClientBuilder",
                    RestClient.Builder.class,
                    (Supplier<RestClient.Builder>) RestClient::builder
            );
            context.registerBean(
                    "orderLoadBalancedRestClientBuilder",
                    RestClient.Builder.class,
                    (Supplier<RestClient.Builder>) RestClient::builder
            );
            context.registerBean(
                    AssistantProperties.class,
                    () -> new AssistantProperties(
                            "https://generativelanguage.googleapis.com/v1beta/openai",
                            "test-key",
                            "gemini-test",
                            2_000,
                            60_000,
                            10,
                            "assistant",
                            "System prompt",
                            0.3,
                            400,
                            0.3,
                            0.7,
                            200,
                            "",
                            false
                    )
            );
            context.registerBean(
                    IntentClassifierProperties.class,
                    () -> new IntentClassifierProperties(1_000, 5_000, "")
            );
            context.registerBean(
                    PolicySearchProperties.class,
                    () -> new PolicySearchProperties(
                            "http://elasticsearch-disabled:9200",
                            "hsmart-policy-index",
                            2,
                            1_000,
                            3_000
                    )
            );
            context.register(AssistantConfig.class, PolicySearchConfig.class);

            context.refresh();

            Assertions.assertNotNull(context.getBean("aiProviderRestClient", RestClient.class));
            Assertions.assertNotNull(context.getBean("intentClassifierRestClient", RestClient.class));
            Assertions.assertNotNull(context.getBean("policySearchRestClient", RestClient.class));
        }
    }
}
