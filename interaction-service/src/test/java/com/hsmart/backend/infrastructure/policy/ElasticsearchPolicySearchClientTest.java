package com.hsmart.backend.infrastructure.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hsmart.backend.application.dto.PolicySearchResult;
import com.hsmart.backend.infrastructure.config.AssistantProperties;
import com.hsmart.backend.infrastructure.config.PolicySearchProperties;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ElasticsearchPolicySearchClientTest {

    @Test
    void searchPoliciesShouldUseFuzzySearchAndSendInternalSecretHeader() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://elasticsearch:9200");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        AssistantProperties assistantProperties = new AssistantProperties(
                "https://api.openai.com/v1", "", "", 2_000, 60_000, 10,
                "h-smart-assistant", "System prompt",
                0.3, 400, 0.3, 0.7, 200, "", false
        );
        ElasticsearchPolicySearchClient client = new ElasticsearchPolicySearchClient(
                restClient,
                new PolicySearchProperties(
                        "http://elasticsearch:9200",
                        "hsmart-policy-index",
                        5,
                        3,
                        0.5,
                        1_000,
                        3_000
                ),
                assistantProperties,
                Optional.empty(),
                "secret-123"
        );

        server.expect(once(), requestTo("http://elasticsearch:9200/hsmart-policy-index/_search"))
                .andExpect(header("X-Internal-Secret", "secret-123"))
                .andExpect(content().string(Matchers.containsString("\"fuzziness\":\"AUTO\"")))
                .andExpect(content().string(Matchers.containsString("\"title^2\"")))
                .andExpect(content().string(Matchers.containsString("\"content\"")))
                .andRespond(withSuccess("""
                        {
                          "hits": {
                            "hits": [
                              {
                                "_score": 2.5,
                                "_source": {
                                  "title": "Return policy",
                                  "category": "Return",
                                  "content": "Buyers can request support when the item does not match the listing."
                                }
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PolicySearchResult> results = client.searchPolicies("doi tra");

        assertEquals(1, results.size());
        assertEquals("Return policy", results.get(0).title());
        assertEquals("Return", results.get(0).category());
        server.verify();
    }
}
