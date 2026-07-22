package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.ai.AiChatClient;
import org.microsoft.qintelipass.ai.AiChatMessage;
import org.microsoft.qintelipass.ai.AiChatResult;
import org.microsoft.qintelipass.entity.Conversation;
import org.microsoft.qintelipass.entity.ConversationMessage;
import org.microsoft.qintelipass.entity.ConversationMessageRole;
import org.microsoft.qintelipass.entity.ConversationMessageStatus;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.repository.ConversationMessageRepository;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.microsoft.qintelipass.request.ConversationTurnRequest;
import org.microsoft.qintelipass.response.ConversationTurnResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationTurnServiceTests {
    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationMessageRepository messageRepository;
    @Mock private AiModelService aiModelService;
    @Mock private ConversationContextService contextService;
    @Mock private AiChatClient aiChatClient;
    @Mock private ConversationTitleGenerator titleGenerator;
    @Mock private TokenCounter tokenCounter;

    private Conversation conversation;
    private ConversationTurnService service;

    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        conversation.setId(1L);
        conversation.setUserId(1001L);
        conversation.setTitle(Conversation.DEFAULT_TITLE);
        conversation.setStatus(Conversation.STATUS_ACTIVE);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setLastSavedAt(LocalDateTime.now());
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        service = new ConversationTurnService(
                conversationRepository, messageRepository, aiModelService, contextService,
                aiChatClient, titleGenerator, tokenCounter, 1000);
    }

    @Test
    void persistsBothMessagesAndGeneratesTitleOnFirstAnswer() {
        AtomicLong ids = new AtomicLong(10);
        when(aiModelService.normalizeOptionalModelKey("deepseek-v4")).thenReturn("deepseek-v4");
        when(messageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(ids.incrementAndGet());
            message.setCreatedAt(LocalDateTime.now());
            return message;
        });
        when(conversationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contextService.prepare(any(), any(), any())).thenReturn(
                new ConversationContextService.PreparedContext(
                        List.of(new AiChatMessage("user", "hello")), 100));
        when(aiChatClient.complete(any(), anyInt(), anyDouble()))
                .thenReturn(new AiChatResult("answer", 100, 20, 120));
        when(titleGenerator.generateTitle("hello", "answer")).thenReturn("测试标题");
        when(tokenCounter.count(any())).thenReturn(2);

        ConversationTurnResponse response = service.send(1001L, 1L, request("hello", "req-1"));

        assertThat(response.userMessage().role()).isEqualTo("USER");
        assertThat(response.assistantMessage().content()).isEqualTo("answer");
        assertThat(response.conversation().title()).isEqualTo("测试标题");
        assertThat(response.contextTokens()).isEqualTo(100);
    }

    @Test
    void returnsExistingTurnWithoutCallingAiAgain() {
        ConversationMessage user = message(11L, ConversationMessageRole.USER, "hello", "req-1");
        ConversationMessage assistant = message(12L, ConversationMessageRole.ASSISTANT, "answer", "req-1");
        when(aiModelService.normalizeOptionalModelKey("deepseek-v4")).thenReturn("deepseek-v4");
        when(messageRepository.findFirstByConversation_IdAndRequestIdAndRole(
                1L, "req-1", ConversationMessageRole.ASSISTANT)).thenReturn(Optional.of(assistant));
        when(messageRepository.findFirstByConversation_IdAndRequestIdAndRole(
                1L, "req-1", ConversationMessageRole.USER)).thenReturn(Optional.of(user));

        ConversationTurnResponse response = service.send(1001L, 1L, request("hello", "req-1"));

        assertThat(response.assistantMessage().id()).isEqualTo(12L);
        verify(aiChatClient, never()).complete(any(), anyInt(), anyDouble());
    }

    @Test
    void rejectsPromptOverTwoThousandUnicodeCharacters() {
        assertThrows(BadRequestException.class,
                () -> service.send(1001L, 1L, request("问".repeat(2001), "req-2")));
        verify(aiChatClient, never()).complete(any(), anyInt(), anyDouble());
    }

    @Test
    void failedFirstAiCallNeverActivatesTheConversation() {
        AtomicReference<Conversation> savedConversation = new AtomicReference<>();
        AtomicLong messageIds = new AtomicLong(20);
        when(aiModelService.normalizeOptionalModelKey("deepseek-v4")).thenReturn("deepseek-v4");
        when(conversationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(1L);
            savedConversation.set(saved);
            return saved;
        });
        when(conversationRepository.findById(1L)).thenAnswer(invocation ->
                Optional.ofNullable(savedConversation.get()));
        when(messageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(messageIds.incrementAndGet());
            message.setCreatedAt(LocalDateTime.now());
            return message;
        });
        when(contextService.prepare(any(), any(), any())).thenReturn(
                new ConversationContextService.PreparedContext(
                        List.of(new AiChatMessage("user", "hello")), 100));
        when(aiChatClient.complete(any(), anyInt(), anyDouble()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThrows(IllegalStateException.class,
                () -> service.sendNew(1001L, request("hello", "req-failed")));

        assertThat(savedConversation.get().getStatus()).isEqualTo(Conversation.STATUS_FAILED);
        assertThat(savedConversation.get().getFirstAnsweredAt()).isNull();
    }

    private ConversationTurnRequest request(String prompt, String requestId) {
        ConversationTurnRequest request = new ConversationTurnRequest();
        request.setPrompt(prompt);
        request.setModelKey("deepseek-v4");
        request.setRequestId(requestId);
        return request;
    }

    private ConversationMessage message(
            Long id,
            ConversationMessageRole role,
            String content,
            String requestId
    ) {
        ConversationMessage message = new ConversationMessage();
        message.setId(id);
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setRequestId(requestId);
        message.setStatus(ConversationMessageStatus.COMPLETED);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}
