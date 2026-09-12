package kz.damulab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeepSeekProviderTranslationTest {

    @Test
    void economyExplanationSendsTheSmallerOutputTokenLimit() {
        RestClient.Builder restClient = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();
        AiProviderProperties properties = new AiProviderProperties();
        properties.getDeepseek().setApiKey("test-key");
        AiTranslationPromptService prompts = mock(AiTranslationPromptService.class);
        when(prompts.renderExplanation(any())).thenReturn(new AiRenderedPrompt("system", "user"));
        DeepSeekProvider provider = new DeepSeekProvider(
                properties,
                mock(AiPromptBuilder.class),
                prompts,
                restClient,
                new ObjectMapper(),
                new AiDraftSchemaValidator()
        );

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"max_tokens":384,"thinking":{"type":"disabled"}}
                        """, false))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"Короткий разбор"}}]}
                        """, MediaType.APPLICATION_JSON));

        AiTextResult result = provider.explainTranslation(new AiTranslationExplanationRequest(
                "Kazakh",
                "Russian",
                "Сәлем",
                "Привет",
                "Russian",
                AiTranslationExplanationMode.ECONOMY
        ), "deepseek-v4-pro");

        assertThat(result.text()).isEqualTo("Короткий разбор");
        server.verify();
    }
}
