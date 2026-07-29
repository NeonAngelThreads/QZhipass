package org.microsoft.qintelipass.agent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.agent.runtime.AgentRuntimeConfig;
import org.microsoft.qintelipass.agent.tool.AgentToolRegistry;
import org.microsoft.qintelipass.agent.tool.CalculatorAgentTool;
import org.microsoft.qintelipass.agent.tool.CurrentTimeAgentTool;
import org.microsoft.qintelipass.ai.AiChatClient;
import org.microsoft.qintelipass.ai.AiChatMessage;
import org.microsoft.qintelipass.ai.AiChatResult;
import org.microsoft.qintelipass.exceptions.AgentInvocationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReActOrchestratorTests {
    @Mock private AiChatClient aiChatClient;

    private ReActOrchestrator orchestrator;
    private AgentRuntimeConfig config;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                new CalculatorAgentTool(),
                new CurrentTimeAgentTool()
        ));
        orchestrator = new ReActOrchestrator(
                aiChatClient,
                registry,
                objectMapper,
                4,
                500,
                2000,
                Duration.ofSeconds(10)
        );
        config = new AgentRuntimeConfig(
                1L,
                1001L,
                "测试Agent",
                "请使用 1. 2. 3. 的编号格式回答。",
                AgentRuntimeConfig.SOURCE_USER,
                "v1",
                List.of("calculator", "current_time"),
                "仅在需要时调用工具。"
        );
    }

    @AfterEach
    void tearDown() {
        orchestrator.closeExecutor();
    }

    @Test
    void returnsDirectFinalAnswerWithoutLeakingDecisionProtocol() {
        when(aiChatClient.complete(any(), anyInt(), anyDouble()))
                .thenReturn(result("""
                        {"type":"final","answer":"1. 结论\\n2. 依据\\n3. 建议"}
                        """, 20, 10));

        ReActResult result = orchestrator.execute(
                config,
                List.of(new AiChatMessage("user", "给我三个建议"))
        );

        assertThat(result.answer()).isEqualTo("1. 结论\n2. 依据\n3. 建议");
        assertThat(result.steps()).isEqualTo(1);
    }

    @Test
    void executesARealCalculatorObservationBeforeFinalAnswer() {
        when(aiChatClient.complete(any(), anyInt(), anyDouble()))
                .thenReturn(
                        result("""
                                {"type":"action","tool":"calculator","arguments":{"expression":"(18+6)/3"}}
                                """, 20, 8),
                        result("""
                                {"type":"final","answer":"计算结果是 8。"}
                                """, 30, 8)
                );

        ReActResult result = orchestrator.execute(
                config,
                List.of(new AiChatMessage("user", "计算 (18+6)/3"))
        );

        assertThat(result.answer()).isEqualTo("计算结果是 8。");
        assertThat(result.steps()).isEqualTo(2);
        assertThat(result.promptTokens()).isEqualTo(50);
    }

    @Test
    void stopsRepeatedActions() {
        AiChatResult action = result("""
                {"type":"action","tool":"calculator","arguments":{"expression":"1+1"}}
                """, 10, 5);
        when(aiChatClient.complete(any(), anyInt(), anyDouble())).thenReturn(action, action);

        assertThrows(
                AgentInvocationException.class,
                () -> orchestrator.execute(config, List.of(new AiChatMessage("user", "计算")))
        );
    }

    @Test
    void rejectsPrivateReasoningInFinalAnswer() {
        when(aiChatClient.complete(any(), anyInt(), anyDouble()))
                .thenReturn(result("""
                        {"type":"final","answer":"Thought: hidden chain\\n最终答案"}
                        """, 10, 5));

        assertThrows(
                AgentInvocationException.class,
                () -> orchestrator.execute(config, List.of(new AiChatMessage("user", "回答问题")))
        );
    }

    @Test
    void enforcesTotalTimeoutDuringModelRequest() {
        when(aiChatClient.complete(any(), anyInt(), anyDouble())).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return result("{\"type\":\"final\",\"answer\":\"too late\"}", 10, 5);
        });
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                new CalculatorAgentTool(),
                new CurrentTimeAgentTool()
        ));
        ReActOrchestrator shortTimeout = new ReActOrchestrator(
                aiChatClient,
                registry,
                new ObjectMapper(),
                4,
                500,
                2000,
                Duration.ofMillis(50)
        );

        try {
            assertThrows(
                    AgentInvocationException.class,
                    () -> shortTimeout.execute(config, List.of(new AiChatMessage("user", "回答问题")))
            );
        } finally {
            shortTimeout.closeExecutor();
        }
    }

    private AiChatResult result(String content, int promptTokens, int completionTokens) {
        return new AiChatResult(
                content,
                promptTokens,
                completionTokens,
                promptTokens + completionTokens
        );
    }
}
