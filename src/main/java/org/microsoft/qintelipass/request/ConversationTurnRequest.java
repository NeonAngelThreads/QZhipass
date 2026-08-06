package org.microsoft.qintelipass.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConversationTurnRequest {
    @NotBlank(message = "prompt must not be blank")
    @Size(max = 4000, message = "encoded prompt is too long")
    private String prompt;

    @Size(max = 100)
    private String modelKey;

    @Size(max = 64)
    private String requestId;
}
