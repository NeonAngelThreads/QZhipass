package org.microsoft.qintelipass.agent.react;

public record ReActResult(
        String answer,
        int promptTokens,
        int completionTokens,
        int steps
) {
}
