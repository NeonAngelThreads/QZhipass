package org.microsoft.qintelipass.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentInvokeRequest {
    @NotNull
    @Positive
    private Long agentId;

    @Positive
    private Long conversationId;

    @NotBlank
    @Size(max = 20_000)
    private String message;
}
