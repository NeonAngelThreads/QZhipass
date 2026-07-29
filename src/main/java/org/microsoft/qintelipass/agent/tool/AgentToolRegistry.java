package org.microsoft.qintelipass.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentToolRegistry {
    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> registeredTools) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        for (AgentTool tool : registeredTools) {
            AgentTool previous = indexed.put(tool.definition().name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Agent tool: " + tool.definition().name());
            }
        }
        this.tools = Map.copyOf(indexed);
    }

    public List<ToolDefinition> definitions(List<String> allowedTools) {
        return allowedTools.stream()
                .map(tools::get)
                .filter(tool -> tool != null)
                .map(AgentTool::definition)
                .toList();
    }

    public ToolExecutionResult execute(String toolName, JsonNode arguments, List<String> allowedTools) {
        if (!allowedTools.contains(toolName)) {
            throw new BadRequestException("Agent attempted to use a tool that is not allowed.");
        }
        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            throw new BadRequestException("Agent attempted to use an unregistered tool.");
        }
        return tool.execute(arguments);
    }

    public ToolDefinition requireDefinition(String toolName, List<String> allowedTools) {
        if (!allowedTools.contains(toolName) || !tools.containsKey(toolName)) {
            throw new BadRequestException("Agent attempted to use an unavailable tool.");
        }
        return tools.get(toolName).definition();
    }
}
