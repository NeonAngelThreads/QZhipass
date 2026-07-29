package org.microsoft.qintelipass.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentInvokeRequest {
    @NotNull(message = "conversationId is required")
    private Long conversationId;

    @NotBlank(message = "prompt must not be blank")
    @Size(max = 4000, message = "encoded prompt is too long")
    private String prompt;

    @Size(max = 100)
    private String modelKey;

    @Size(max = 64)
    private String requestId;

    public ConversationTurnRequest toConversationTurnRequest(Long agentId) {
        ConversationTurnRequest request = new ConversationTurnRequest();
        request.setPrompt(prompt);
        request.setModelKey(modelKey);
        request.setRequestId(requestId);
        request.setAgentId(agentId);
        return request;
    }
}
