package org.microsoft.qintelipass.agent.react;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReActDecision(
        String type,
        String tool,
        JsonNode arguments,
        String answer
) {
}
