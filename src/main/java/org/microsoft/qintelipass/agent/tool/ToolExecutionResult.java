package org.microsoft.qintelipass.agent.tool;

import java.util.Map;

public record ToolExecutionResult(
        String tool,
        boolean success,
        Map<String, Object> data,
        String errorCode
) {
    public static ToolExecutionResult success(String tool, Map<String, Object> data) {
        return new ToolExecutionResult(tool, true, data, null);
    }

    public static ToolExecutionResult error(String tool, String errorCode) {
        return new ToolExecutionResult(tool, false, Map.of(), errorCode);
    }
}
