package org.microsoft.qintelipass.agent.tool;

import java.time.Duration;

public record ToolDefinition(
        String name,
        String description,
        String parameterSchema,
        Duration timeout,
        boolean hasSideEffects
) {
}
