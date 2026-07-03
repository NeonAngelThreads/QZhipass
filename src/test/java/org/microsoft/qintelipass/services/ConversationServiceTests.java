package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.entity.AiModelConfig;
import org.microsoft.qintelipass.entity.Conversation;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.ForbiddenException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.repository.AiModelConfigRepository;
import org.microsoft.qintelipass.repository.ConversationMessageRepository;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.microsoft.qintelipass.request.CreateConversationRequest;
import org.microsoft.qintelipass.request.SaveConversationMessageRequest;
import org.microsoft.qintelipass.request.UpdateConversationModelRequest;
import org.microsoft.qintelipass.request.UpdateConversationTitleRequest;
import org.microsoft.qintelipass.response.ConversationDetailResponse;
import org.microsoft.qintelipass.response.ConversationMessageResponse;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ConversationSummaryResponse;
import org.microsoft.qintelipass.response.ModelResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qzhipass_conversations;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ConversationServiceTests {
    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AiModelService aiModelService;

    @Autowired
    private AiModelConfigRepository modelConfigRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMessageRepository messageRepository;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        modelConfigRepository.deleteAll();
        modelConfigRepository.save(model("gpt4-omni", "GPT-4 Omni", "OPENAI", true, 10));
        modelConfigRepository.save(model("gpt4-turbo", "GPT-4 Turbo", "OPENAI", true, 20));
        modelConfigRepository.save(model("claude-3.5", "Claude 3.5 Sonnet", "ANTHROPIC", true, 30));
        modelConfigRepository.save(model("qwen3", "千问3", "ALIBABA", true, 40));
        modelConfigRepository.save(model("deepseek-v4", "DeepSeek-V4", "DEEPSEEK", true, 50));
        modelConfigRepository.save(model("disabled-model", "Disabled Model", "LOCAL_DEMO", false, 60));
    }

    @Test
    void createsBlankConversationWithDefaultTitleAndUniqueIds() {
        ConversationResponse first = conversationService.createConversation("user-1", null);
        ConversationResponse second = conversationService.createConversation("user-1", null);

        assertThat(first.title()).isEqualTo(Conversation.DEFAULT_TITLE);
        assertThat(second.title()).isEqualTo(Conversation.DEFAULT_TITLE);
        assertThat(first.id()).isNotNull();
        assertThat(second.id()).isNotNull();
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void listsOnlyCurrentUserConversationsInRecentOrder() throws InterruptedException {
        ConversationResponse first = conversationService.createConversation("user-1", null);
        Thread.sleep(5);
        conversationService.createConversation("user-2", null);
        Thread.sleep(5);
        ConversationResponse second = conversationService.createConversation("user-1", null);

        List<ConversationSummaryResponse> initialList = conversationService.listRecentConversations("user-1", 20);
        assertThat(initialList).extracting(ConversationSummaryResponse::id).containsExactly(second.id(), first.id());

        Thread.sleep(5);
        conversationService.saveMessage("user-1", first.id(), message("USER", "update older conversation", null));

        List<ConversationSummaryResponse> updatedList = conversationService.listRecentConversations("user-1", 20);
        assertThat(updatedList).extracting(ConversationSummaryResponse::id).containsExactly(first.id(), second.id());
        assertThat(updatedList).extracting(ConversationSummaryResponse::messageCount).containsExactly(1L, 0L);
    }

    @Test
    void rejectsAccessToAnotherUsersConversation() {
        ConversationResponse conversation = conversationService.createConversation("user-1", null);

        assertThrows(
                ForbiddenException.class,
                () -> conversationService.getConversation("user-2", conversation.id())
        );
        assertThrows(
                ForbiddenException.class,
                () -> conversationService.updateModel("user-2", conversation.id(), updateModel("gpt4-omni"))
        );
    }

    @Test
    void savesUserAndAssistantMessagesAndGeneratesTitleOnce() {
        ConversationResponse conversation = conversationService.createConversation("user-1", create("gpt4-omni"));

        ConversationMessageResponse userMessage = conversationService.saveMessage(
                "user-1",
                conversation.id(),
                message("USER", "  帮我分析年度预算\n和现金流  ", null)
        );
        ConversationMessageResponse assistantMessage = conversationService.saveMessage(
                "user-1",
                conversation.id(),
                message("ASSISTANT", "可以，下面是预算分析。", null)
        );

        ConversationDetailResponse detail = conversationService.getConversation("user-1", conversation.id());
        assertThat(userMessage.role()).isEqualTo("USER");
        assertThat(assistantMessage.role()).isEqualTo("ASSISTANT");
        assertThat(detail.conversation().title()).isEqualTo("帮我分析年度预算 和现金流");
        assertThat(detail.messages()).extracting(ConversationMessageResponse::role).containsExactly("USER", "ASSISTANT");
        assertThat(detail.model()).isEqualTo(new ModelResponse("gpt4-omni", "GPT-4 Omni", "OPENAI"));

        conversationService.saveMessage("user-1", conversation.id(), message("ASSISTANT", "第二次回复", null));
        ConversationDetailResponse afterSecondAssistant = conversationService.getConversation("user-1", conversation.id());
        assertThat(afterSecondAssistant.conversation().title()).isEqualTo("帮我分析年度预算 和现金流");
    }

    @Test
    void doesNotOverwriteCustomizedTitleAfterFirstAssistantMessage() {
        ConversationResponse conversation = conversationService.createConversation("user-1", null);
        conversationService.saveMessage("user-1", conversation.id(), message("USER", "默认标题不应该覆盖", null));
        conversationService.updateTitle("user-1", conversation.id(), updateTitle("人工标题"));

        conversationService.saveMessage("user-1", conversation.id(), message("ASSISTANT", "首次 AI 回复", null));

        ConversationDetailResponse detail = conversationService.getConversation("user-1", conversation.id());
        assertThat(detail.conversation().title()).isEqualTo("人工标题");
    }

    @Test
    void updatesConversationModelWhenModelIsAvailable() {
        ConversationResponse conversation = conversationService.createConversation("user-1", null);

        ConversationResponse updated = conversationService.updateModel(
                "user-1",
                conversation.id(),
                updateModel("qwen3")
        );

        assertThat(updated.modelKey()).isEqualTo("qwen3");
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(
                BadRequestException.class,
                () -> conversationService.createConversation("user-1", create("missing-model"))
        );

        ConversationResponse conversation = conversationService.createConversation("user-1", null);

        assertThrows(
                BadRequestException.class,
                () -> conversationService.createConversation("user-1", create("disabled-model"))
        );
        assertThrows(
                BadRequestException.class,
                () -> conversationService.saveMessage("user-1", conversation.id(), message("USER", " ", null))
        );
        assertThrows(
                BadRequestException.class,
                () -> conversationService.saveMessage("user-1", conversation.id(), message("UNKNOWN", "content", null))
        );
        assertThrows(
                NotFoundException.class,
                () -> conversationService.getConversation("user-1", 999999L)
        );
    }

    @Test
    void returnsOnlyEnabledModels() {
        List<ModelResponse> models = aiModelService.listAvailableModels("user-1");

        assertThat(models).containsExactly(
                new ModelResponse("gpt4-omni", "GPT-4 Omni", "OPENAI"),
                new ModelResponse("gpt4-turbo", "GPT-4 Turbo", "OPENAI"),
                new ModelResponse("claude-3.5", "Claude 3.5 Sonnet", "ANTHROPIC"),
                new ModelResponse("qwen3", "千问3", "ALIBABA"),
                new ModelResponse("deepseek-v4", "DeepSeek-V4", "DEEPSEEK")
        );
    }

    private AiModelConfig model(String modelKey, String displayName, String provider, boolean enabled, int sortOrder) {
        AiModelConfig model = new AiModelConfig();
        model.setModelKey(modelKey);
        model.setDisplayName(displayName);
        model.setProvider(provider);
        model.setEnabled(enabled);
        model.setSortOrder(sortOrder);
        return model;
    }

    private CreateConversationRequest create(String modelKey) {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setModelKey(modelKey);
        return request;
    }

    private SaveConversationMessageRequest message(String role, String content, String modelKey) {
        SaveConversationMessageRequest request = new SaveConversationMessageRequest();
        request.setRole(role);
        request.setContent(content);
        request.setModelKey(modelKey);
        return request;
    }

    private UpdateConversationModelRequest updateModel(String modelKey) {
        UpdateConversationModelRequest request = new UpdateConversationModelRequest();
        request.setModelKey(modelKey);
        return request;
    }

    private UpdateConversationTitleRequest updateTitle(String title) {
        UpdateConversationTitleRequest request = new UpdateConversationTitleRequest();
        request.setTitle(title);
        return request;
    }
}
