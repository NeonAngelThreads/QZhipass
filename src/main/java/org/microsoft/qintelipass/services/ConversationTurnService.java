package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.agent.react.ReActResult;
import org.microsoft.qintelipass.ai.AiChatClient;
import org.microsoft.qintelipass.ai.AiChatResult;
import org.microsoft.qintelipass.enums.ConversationMessageRole;
import org.microsoft.qintelipass.enums.ConversationMessageStatus;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.ForbiddenException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.models.Conversation;
import org.microsoft.qintelipass.models.ConversationMessage;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.ConversationMessageRepository;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.microsoft.qintelipass.request.ConversationTurnRequest;
import org.microsoft.qintelipass.response.ConversationMessageResponse;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ConversationTurnResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConversationTurnService {
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AiModelService aiModelService;
    private final ConversationContextService contextService;
    private final AiChatClient aiChatClient;
    private final AgentInvocationService agentInvocationService;
    private final ConversationTitleGenerator titleGenerator;
    private final TokenCounter tokenCounter;
    private final CensorService censorService;
    private final UserService userService;
    private final int maxCompletionTokens;

    public ConversationTurnService(
            ConversationRepository conversationRepository,
            ConversationMessageRepository messageRepository,
            AiModelService aiModelService,
            ConversationContextService contextService,
            AiChatClient aiChatClient,
            AgentInvocationService agentInvocationService,
            ConversationTitleGenerator titleGenerator,
            TokenCounter tokenCounter,
            CensorService censorService,
            UserService userService,
            @Value("${ai.completion.max-tokens:1000}") int maxCompletionTokens
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.aiModelService = aiModelService;
        this.contextService = contextService;
        this.aiChatClient = aiChatClient;
        this.agentInvocationService = agentInvocationService;
        this.titleGenerator = titleGenerator;
        this.tokenCounter = tokenCounter;
        this.censorService = censorService;
        this.userService = userService;
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public ConversationTurnResponse send(Long userId, Long conversationId, ConversationTurnRequest request) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        String prompt = normalizePrompt(request == null ? null : request.getPrompt());
        String requestId = normalizeRequestId(request == null ? null : request.getRequestId());
        String modelKey = aiModelService.normalizeOptionalModelKey(request == null ? null : request.getModelKey());
        if (modelKey == null) {
            modelKey = conversation.getModelKey();
        }
        final String effectiveModelKey = modelKey;

        ConversationMessage existingAssistant = messageRepository
                .findFirstByConversation_IdAndRequestIdAndRole(
                        conversationId, requestId, ConversationMessageRole.ASSISTANT)
                .orElse(null);
        if (existingAssistant != null && existingAssistant.getStatus() == ConversationMessageStatus.COMPLETED) {
            ConversationMessage existingUser = messageRepository
                    .findFirstByConversation_IdAndRequestIdAndRole(
                            conversationId, requestId, ConversationMessageRole.USER)
                    .orElseThrow();
            return new ConversationTurnResponse(
                    ConversationResponse.from(conversation),
                    ConversationMessageResponse.from(existingUser),
                    ConversationMessageResponse.from(existingAssistant),
                    0, 0, 0
            );
        }

        ConversationMessage userMessage = findOrCreateMessage(
                conversation, ConversationMessageRole.USER, prompt, effectiveModelKey, requestId, null);

        touchConversation(conversation, false);
        conversation = conversationRepository.saveAndFlush(conversation);

        ConversationContextService.PreparedContext context =
                contextService.prepare(conversation, userMessage.getId(), prompt);
        Long agentId = request == null ? null : request.getAgentId();
        AiChatResult result;
        if (agentId == null) {
            result = aiChatClient.complete(context.messages(), maxCompletionTokens, 0.7);
        } else {
            ReActResult agentResult = agentInvocationService.invoke(
                    userId, agentId, context.messages(), requestId);
            result = new AiChatResult(
                    agentResult.answer(),
                    agentResult.promptTokens(),
                    agentResult.completionTokens(),
                    agentResult.promptTokens() + agentResult.completionTokens()
            );
        }

        ConversationMessage assistantMessage = findOrCreateMessage(
                conversation, ConversationMessageRole.ASSISTANT, result.content(), effectiveModelKey, requestId, agentId);
        checkSensitiveContent(userId, prompt, assistantMessage.getContent(), effectiveModelKey);
        boolean firstAnswer = conversation.getFirstAnsweredAt() == null;
        touchConversation(conversation, true);
        if (firstAnswer) {
            conversation.setFirstAnsweredAt(LocalDateTime.now());
        }
        if (firstAnswer && !conversation.isTitleGenerated() && !conversation.isTitleCustomized()) {
            conversation.setTitle(titleGenerator.generateTitle(prompt, assistantMessage.getContent()));
            conversation.setTitleGenerated(true);
        }
        conversation.setStatus(Conversation.STATUS_ACTIVE);
        conversation = conversationRepository.saveAndFlush(conversation);

        return new ConversationTurnResponse(
                ConversationResponse.from(conversation),
                ConversationMessageResponse.from(userMessage),
                ConversationMessageResponse.from(assistantMessage),
                context.estimatedTokens(),
                result.promptTokens(),
                result.completionTokens()
        );
    }

    public ConversationTurnResponse sendNew(Long userId, ConversationTurnRequest request) {
        normalizePrompt(request == null ? null : request.getPrompt());
        normalizeRequestId(request == null ? null : request.getRequestId());
        String modelKey = aiModelService.normalizeOptionalModelKey(request == null ? null : request.getModelKey());

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(Conversation.DEFAULT_TITLE);
        conversation.setModelKey(modelKey);
        conversation.setStatus(Conversation.STATUS_PENDING);
        conversation = conversationRepository.saveAndFlush(conversation);
        try {
            return send(userId, conversation.getId(), request);
        } catch (RuntimeException failure) {
            markFirstTurnFailed(conversation.getId());
            throw failure;
        }
    }

    private ConversationMessage saveMessage(
            Conversation conversation,
            ConversationMessageRole role,
            String content,
            String modelKey,
            String requestId,
            Long agentId,
            ConversationMessageStatus status
    ) {
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setModelKey(modelKey);
        message.setRequestId(requestId);
        message.setAgentId(agentId);
        message.setStatus(status);
        message.setTokenCount(tokenCounter.count(content));
        return messageRepository.saveAndFlush(message);
    }

    private ConversationMessage findOrCreateMessage(
            Conversation conversation,
            ConversationMessageRole role,
            String content,
            String modelKey,
            String requestId,
            Long agentId
    ) {
        ConversationMessage existing = messageRepository
                .findFirstByConversation_IdAndRequestIdAndRole(conversation.getId(), requestId, role)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        try {
            return saveMessage(
                    conversation, role, content, modelKey, requestId, agentId,
                    ConversationMessageStatus.COMPLETED);
        } catch (DataIntegrityViolationException duplicateRequest) {
            return messageRepository
                    .findFirstByConversation_IdAndRequestIdAndRole(conversation.getId(), requestId, role)
                    .orElseThrow(() -> duplicateRequest);
        }
    }

    private void touchConversation(Conversation conversation, boolean answered) {
        LocalDateTime now = LocalDateTime.now();
        conversation.setUpdatedAt(now);
        conversation.setLastMessageAt(now);
        conversation.setLastSavedAt(now);
        if (answered && conversation.getFirstAnsweredAt() == null) {
            conversation.setFirstAnsweredAt(now);
        }
    }

    private void checkSensitiveContent(
            Long userId,
            String input,
            String output,
            String modelKey
    ) {
        try {
            User user = userService.getUserById(userId);
            if (user == null) {
                return;
            }
            censorService.checkAndRecord(
                    userId,
                    user.getName(),
                    user.getPhone(),
                    user.getDepartment() == null ? "" : user.getDepartment(),
                    modelKey == null ? "" : modelKey,
                    input,
                    output
            );
        } catch (RuntimeException ignored) {
            // Censor recording must not make a completed AI turn fail.
        }
    }

    private void markFirstTurnFailed(Long conversationId) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setStatus(Conversation.STATUS_FAILED);
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.saveAndFlush(conversation);
        });
    }

    private Conversation requireOwnedConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation does not exist."));
        if (!conversation.getUserId().equals(userId)) {
            throw new ForbiddenException("Conversation does not belong to current user.");
        }
        return conversation;
    }

    private String normalizePrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new BadRequestException("prompt must not be blank.");
        }
        String normalized = prompt.trim();
        if (normalized.codePointCount(0, normalized.length()) > 2_000) {
            throw new BadRequestException("prompt must not exceed 2000 characters.");
        }
        return normalized;
    }

    private String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return UUID.randomUUID().toString();
        }
        String normalized = requestId.trim();
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new BadRequestException("requestId format is invalid.");
        }
        return normalized;
    }
}
