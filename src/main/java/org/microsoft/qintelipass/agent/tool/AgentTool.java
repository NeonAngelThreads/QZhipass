package org.microsoft.qintelipass.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {
    ToolDefinition definition();

    ToolExecutionResult execute(JsonNode arguments);
}
