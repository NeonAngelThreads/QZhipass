package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.ai.AiChatClient;
import org.microsoft.qintelipass.ai.AiChatResult;
import org.microsoft.qintelipass.entity.Conversation;
import org.microsoft.qintelipass.entity.ConversationMemory;
import org.microsoft.qintelipass.entity.ConversationMessage;
import org.microsoft.qintelipass.entity.ConversationMessageRole;
import org.microsoft.qintelipass.entity.ConversationMessageStatus;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.repository.ConversationMemoryRepository;
import org.microsoft.qintelipass.repository.ConversationMessageRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationContextServiceTests {
    @Mock
    private ConversationMessageRepository messageRepository;
    @Mock
    private ConversationMemoryRepository memoryRepository;
    @Mock
    private AiChatClient aiChatClient;

    private Conversation conversation;
    private ConversationContextService service;

    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        conversation.setId(1L);
        service = new ConversationContextService(
                messageRepository,
                memoryRepository,
                new ConservativeTokenCounter(),
                aiChatClient,
                "system",
                4000,
                2000,
                80
        );
        when(memoryRepository.findById(1L)).thenReturn(Optional.empty());
    }

    @Test
    void keepsMoreThanFiveShortTurnsWhenTheyFitRawTokenBudget() {
        List<ConversationMessage> history = history(8, "short question", "short answer");
        when(messageRepository.findByConversation_IdAndStatusOrderByCreatedAtAscIdAsc(
                1L, ConversationMessageStatus.COMPLETED)).thenReturn(history);

        ConversationContextService.PreparedContext prepared = service.prepare(conversation, 999L, "latest");

        assertThat(prepared.messages()).hasSize(18);
        assertThat(prepared.estimatedTokens()).isLessThanOrEqualTo(4000);
        verify(aiChatClient, never()).complete(any(), anyInt(), anyDouble());
    }

    @Test
    void summarizesOlderHistoryAndNeverExceedsFourThousandTokens() {
        List<ConversationMessage> history = history(5, "问".repeat(600), "答".repeat(600));
        when(messageRepository.findByConversation_IdAndStatusOrderByCreatedAtAscIdAsc(
                1L, ConversationMessageStatus.COMPLETED)).thenReturn(history);
        when(aiChatClient.complete(any(), anyInt(), anyDouble()))
                .thenReturn(new AiChatResult("摘要".repeat(250), 1000, 500, 1500));
        when(memoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationContextService.PreparedContext prepared =
                service.prepare(conversation, 999L, "新".repeat(1500));

        assertThat(prepared.estimatedTokens()).isLessThanOrEqualTo(4000);
        assertThat(prepared.messages()).anyMatch(message -> message.content().contains("更早对话的摘要"));
        ArgumentCaptor<ConversationMemory> captor = ArgumentCaptor.forClass(ConversationMemory.class);
        verify(memoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSummarizedThroughMessageId()).isEqualTo(8L);
    }

    @Test
    void rejectsLatestPromptWhenItAloneWouldExceedTheContextBudget() {
        when(messageRepository.findByConversation_IdAndStatusOrderByCreatedAtAscIdAsc(
                1L, ConversationMessageStatus.COMPLETED)).thenReturn(List.of());

        assertThrows(BadRequestException.class,
                () -> service.prepare(conversation, 999L, "🙂".repeat(2000)));
    }

    private List<ConversationMessage> history(int turns, String userContent, String assistantContent) {
        List<ConversationMessage> messages = new ArrayList<>();
        long id = 1;
        for (int turn = 0; turn < turns; turn++) {
            messages.add(message(id++, ConversationMessageRole.USER, userContent + turn));
            messages.add(message(id++, ConversationMessageRole.ASSISTANT, assistantContent + turn));
        }
        return messages;
    }

    private ConversationMessage message(long id, ConversationMessageRole role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setId(id);
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setStatus(ConversationMessageStatus.COMPLETED);
        return message;
    }
}
