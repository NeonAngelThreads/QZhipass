package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.microsoft.qintelipass.entity.AiModelConfig;
import org.microsoft.qintelipass.repository.AiModelConfigRepository;
import org.microsoft.qintelipass.repository.ConversationMessageRepository;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.microsoft.qintelipass.request.ConversationTurnRequest;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ConversationTurnResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_REAL_AI_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qzhipass_real_ai;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ai.base-url=https://api.deepseek.com/v1",
        "ai.chat-model=deepseek-chat"
})
class RealAiConversationSmokeTest {
    private static final Long USER_ID = 9001L;

    @Autowired private ConversationService conversationService;
    @Autowired private ConversationTurnService conversationTurnService;
    @Autowired private AiModelConfigRepository modelRepository;
    @Autowired private ConversationMessageRepository messageRepository;
    @Autowired private ConversationRepository conversationRepository;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        modelRepository.deleteAll();

        AiModelConfig model = new AiModelConfig();
        model.setModelKey("deepseek-v4");
        model.setDisplayName("DeepSeek-V4");
        model.setProvider("DEEPSEEK");
        model.setEnabled(true);
        model.setSortOrder(1);
        modelRepository.save(model);
    }

    @Test
    void completesAndPersistsARealAiTurn() {
        ConversationResponse conversation = conversationService.createConversation(USER_ID, null);
        ConversationTurnRequest request = new ConversationTurnRequest();
        request.setPrompt("这是一次后端联调测试，请用一句简短中文确认服务正常。");
        request.setModelKey("deepseek-v4");
        request.setRequestId("real-ai-smoke-1");

        ConversationTurnResponse turn = conversationTurnService.send(USER_ID, conversation.id(), request);

        assertThat(turn.assistantMessage().content()).isNotBlank();
        assertThat(turn.contextTokens()).isBetween(1, 4000);
        assertThat(turn.conversation().title()).isNotBlank();
        assertThat(turn.conversation().title().codePointCount(0, turn.conversation().title().length()))
                .isLessThanOrEqualTo(25);
        assertThat(conversationService.getConversation(USER_ID, conversation.id()).messages()).hasSize(2);
    }
}
