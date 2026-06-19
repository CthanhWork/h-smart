package com.hsmart.backend.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.AssistantChatMessage;
import com.hsmart.backend.application.exceptions.AssistantGatewayTimeoutException;
import com.hsmart.backend.application.exceptions.AssistantServiceUnavailableException;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CloudAssistantClientTest {

    @Test
    void generateReplyShouldCallOpenAiCompatibleChatCompletionsEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudAssistantClient client = new CloudAssistantClient(builder.build(), properties("test-key", "gpt-test"), new ObjectMapper());

        server.expect(once(), requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(Matchers.containsString("\"model\":\"gpt-test\"")))
                .andExpect(content().string(Matchers.containsString("\"stream\":false")))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "Xin chao, minh co the giup gi?"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String reply = client.generateReply(List.of(new AssistantChatMessage("user", "Hello")));

        assertEquals("Xin chao, minh co the giup gi?", reply);
        server.verify();
    }

    @Test
    void generateReplyShouldFailFastWhenApiKeyIsMissing() {
        CloudAssistantClient client = new CloudAssistantClient(
                RestClient.builder().baseUrl("https://api.openai.com/v1").build(),
                properties("", "gpt-test"),
                new ObjectMapper()
        );

        assertThrows(
                AssistantServiceUnavailableException.class,
                () -> client.generateReply(List.of(new AssistantChatMessage("user", "Hello")))
        );
    }

    @Test
    void generateReplyShouldMapProviderGatewayTimeoutToGatewayTimeoutException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudAssistantClient client = new CloudAssistantClient(builder.build(), properties("test-key", "gpt-test"), new ObjectMapper());

        server.expect(once(), requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThrows(
                AssistantGatewayTimeoutException.class,
                () -> client.generateReply(List.of(new AssistantChatMessage("user", "Hello")))
        );
        server.verify();
    }

    private AssistantProperties properties(String apiKey, String model) {
        return new AssistantProperties(
                "https://api.openai.com/v1",
                apiKey,
                model,
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
        );
    }
}
