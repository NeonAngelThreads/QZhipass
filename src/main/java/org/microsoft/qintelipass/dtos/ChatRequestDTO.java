package org.microsoft.qintelipass.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request body for AI chat endpoints.
 */
@Data
public class ChatRequestDTO {

    /** Model key (must exist in ai_model_configs table). Optional — uses conversation default or system default. */
    private String modelKey;

    /** The current user message text. */
    @NotBlank(message = "message must not be blank")
    @Size(max = 20_000, message = "message too long")
    private String message;

    /** Optional system prompt override. */
    @Size(max = 4_000)
    private String systemPrompt;

    /** Optional image URLs / data URIs for multi-modal models. */
    private List<String> imageUrls;

    /** Model temperature (0.0 – 2.0). Null = use default. */
    private Float temperature;

    /** Max completion tokens. Null = use default. */
    private Integer maxTokens;
}