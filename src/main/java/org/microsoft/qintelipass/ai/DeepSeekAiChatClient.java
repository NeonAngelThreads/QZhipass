package org.microsoft.qintelipass.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Component
public class DeepSeekAiChatClient implements AiChatClient {
    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public DeepSeekAiChatClient(
            WebClient.Builder builder,
            @Value("${ai.base-url:https://api.deepseek.com/v1}") String baseUrl,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.chat-model:deepseek-chat}") String model
    ) {
        this.webClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public AiChatResult complete(List<AiChatMessage> messages, int maxCompletionTokens, double temperature) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BadRequestException("AI_API_KEY is not configured.");
        }

        CompletionResponse response;
        try {
            response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .bodyValue(new CompletionRequest(model, messages, temperature, maxCompletionTokens, false))
                    .retrieve()
                    .bodyToMono(CompletionResponse.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw new AiProviderException("AI provider rejected the request (HTTP "
                    + exception.getStatusCode().value() + ").");
        } catch (WebClientException exception) {
            throw new AiProviderException("AI provider is temporarily unavailable.");
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || !StringUtils.hasText(response.choices().getFirst().message().content())) {
            throw new AiProviderException("AI provider returned an empty response.");
        }
        Usage usage = response.usage();
        return new AiChatResult(
                response.choices().getFirst().message().content().trim(),
                usage == null || usage.promptTokens() == null ? 0 : usage.promptTokens(),
                usage == null || usage.completionTokens() == null ? 0 : usage.completionTokens(),
                usage == null || usage.totalTokens() == null ? 0 : usage.totalTokens()
        );
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record CompletionRequest(
            String model,
            List<AiChatMessage> messages,
            Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens,
            Boolean stream
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompletionResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(AiChatMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
