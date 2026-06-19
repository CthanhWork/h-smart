package com.hsmart.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.IntentClassification;
import com.hsmart.backend.application.dto.IntentClassification.Intent;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.tracing.Tracer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IntentClassifierTest {

    @Test
    void classifyShouldSendStrictPromptAndParseSystemIntent() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        IntentClassifier classifier = newClassifier(restClient, CircuitBreakerRegistry.ofDefaults());

        server.expect(once(), requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(Matchers.containsString("qwen2.5:3b-instruct")))
                .andExpect(content().string(Matchers.containsString("Return only valid JSON")))
                .andExpect(content().string(Matchers.containsString("SYSTEM|POLICY|GENERAL")))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "{\\"intent\\":\\"SYSTEM\\",\\"reason\\":\\"User asks about trust score\\"}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        IntentClassification classification = classifier.classify("Diem uy tin cua toi la bao nhieu?");

        assertEquals(Intent.SYSTEM, classification.intent());
        assertEquals("User asks about trust score", classification.reason());
        server.verify();
    }

    @Test
    void classifyShouldFallbackToGeneralWhenCircuitBreakerIsOpen() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker circuitBreaker = registry.circuitBreaker(IntentClassifier.INTENT_CLASSIFIER_CIRCUIT_BREAKER);
        circuitBreaker.transitionToOpenState();

        IntentClassifier classifier = newClassifier(
                RestClient.builder().baseUrl("https://api.openai.com/v1").build(),
                registry
        );

        IntentClassification classification = classifier.classify("Kiem tra don hang giup toi");

        assertEquals(Intent.GENERAL, classification.intent());
        assertEquals("Intent classifier is unavailable", classification.reason());
    }

    @Test
    void classifyShouldFallbackToGeneralWhenJsonIsInvalid() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        IntentClassifier classifier = newClassifier(restClient, CircuitBreakerRegistry.ofDefaults());

        server.expect(once(), requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "SYSTEM"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        IntentClassification classification = classifier.classify("Cho toi xem tai khoan");

        assertEquals(Intent.GENERAL, classification.intent());
        assertEquals("Intent classifier is unavailable", classification.reason());
        server.verify();
    }

    private IntentClassifier newClassifier(RestClient restClient, CircuitBreakerRegistry registry) {
        return new IntentClassifier(
                restClient,
                new AssistantProperties(
                        "https://api.openai.com/v1",
                        "test-key",
                        "qwen2.5:3b-instruct",
                        2_000,
                        60_000,
                        10,
                        "h-smart-assistant",
                        "You are H-Smart Assistant",
                        0.3,
                        400,
                        0.3,
                        0.7,
                        200,
                        "",
                        false
                ),
                registry,
                new ObjectMapper(),
                org.mockito.Mockito.mock(Tracer.class)
        );
    }
}
