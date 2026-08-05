package org.microsoft.qintelipass.services.runtime;

import java.util.List;

public record AgentRuntimeConfig(
        Long agentId,
        Long ownerUserId,
        String name,
        String prompt,
        String source,
        String version,
        List<String> allowedTools,
        String localInstructions
) {
    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_TEMPLATE = "TEMPLATE";
}