package org.microsoft.qintelipass.response;

public record AgentStreamEvent(
        String type,
        String message,
        String content,
        String agentName,
        ConversationTurnResponse turn
) {
    public static AgentStreamEvent status(String type, String message, String agentName) {
        return new AgentStreamEvent(type, message, null, agentName, null);
    }

    public static AgentStreamEvent content(String content, String agentName) {
        return new AgentStreamEvent("content", null, content, agentName, null);
    }

    public static AgentStreamEvent complete(ConversationTurnResponse turn, String agentName) {
        return new AgentStreamEvent("complete", null, null, agentName, turn);
    }

    public static AgentStreamEvent error() {
        return new AgentStreamEvent("error", "Agent调用失败，请稍后重试。", null, null, null);
    }
}
