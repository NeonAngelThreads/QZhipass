package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.ForbiddenException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.models.Conversation;
import org.microsoft.qintelipass.models.ConversationMessage;
import org.microsoft.qintelipass.models.ConversationMessageRole;
import org.microsoft.qintelipass.repository.ConversationMessageRepository;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.microsoft.qintelipass.request.CreateConversationRequest;
import org.microsoft.qintelipass.request.SaveConversationMessageRequest;
import org.microsoft.qintelipass.request.UpdateConversationModelRequest;
import org.microsoft.qintelipass.request.UpdateConversationTitleRequest;
import org.microsoft.qintelipass.response.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
// Core conversation workflow: create chats, save messages, update titles/models, and enforce ownership.
public class ConversationService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_MESSAGE_LENGTH = 20_000;
    private static final int MAX_TITLE_LENGTH = 60;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AiModelService aiModelService;
    private final ConversationTitleGenerator titleGenerator;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMessageRepository messageRepository,
            AiModelService aiModelService,
            ConversationTitleGenerator titleGenerator
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.aiModelService = aiModelService;
        this.titleGenerator = titleGenerator;
    }

    @Transactional
    // Creates a blank conversation owned by the current MySQL user id.
    public ConversationResponse createConversation(Long userId, CreateConversationRequest request) {
        String modelKey = aiModelService.normalizeOptionalModelKey(request == null ? null : request.getModelKey());

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(Conversation.DEFAULT_TITLE);
        conversation.setModelKey(modelKey);
        conversation.setStatus(Conversation.STATUS_ACTIVE);

        return ConversationResponse.from(conversationRepository.save(conversation));
    }

    @Transactional
    public ConversationResponse createInitialConversation(Long userId) {
        return createConversation(userId, null);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listRecentConversations(Long userId, Integer limit) {
        int safeLimit = normalizeLimit(limit);
        return conversationRepository
                .findByUserIdOrderByLastMessageAtDescUpdatedAtDesc(userId, PageRequest.of(0, safeLimit))
                .stream()
                .map(conversation -> ConversationSummaryResponse.from(
                        conversation,
                        messageRepository.countByConversation_Id(conversation.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversation(Long userId, Long conversationId) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        List<ConversationMessageResponse> messages = messageRepository
                .findByConversation_IdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(ConversationMessageResponse::from)
                .toList();
        ModelResponse model = (ModelResponse) aiModelService.findAvailableModel(conversation.getModelKey()).orElse(null);
        return new ConversationDetailResponse(ConversationResponse.from(conversation), messages, model);
    }

    @Transactional
    // Saves USER, ASSISTANT, or SYSTEM messages after conversationId + userId ownership validation.
    public ConversationMessageResponse saveMessage(
            Long userId,
            Long conversationId,
            SaveConversationMessageRequest request
    ) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        ConversationMessageRole role = parseRole(request == null ? null : request.getRole());
        String content = normalizeMessageContent(request == null ? null : request.getContent());
        String modelKey = aiModelService.normalizeOptionalModelKey(request == null ? null : request.getModelKey());
        if (modelKey == null) {
            modelKey = conversation.getModelKey();
        }

        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setModelKey(modelKey);

        ConversationMessage savedMessage = messageRepository.save(message);
        LocalDateTime now = LocalDateTime.now();
        conversation.setUpdatedAt(now);
        conversation.setLastMessageAt(now);

        updateDefaultTitleAfterFirstAssistantMessage(conversation, role, content);
        return ConversationMessageResponse.from(savedMessage);
    }

    @Transactional
    public ConversationResponse updateModel(
            Long userId,
            Long conversationId,
            UpdateConversationModelRequest request
    ) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        String modelKey = aiModelService.requireAvailableModelKey(request == null ? null : request.getModelKey());
        conversation.setModelKey(modelKey);
        conversation.setUpdatedAt(LocalDateTime.now());
        return ConversationResponse.from(conversation);
    }

    @Transactional
    public ConversationResponse updateTitle(
            Long userId,
            Long conversationId,
            UpdateConversationTitleRequest request
    ) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        String title = normalizeTitle(request == null ? null : request.getTitle());
        conversation.setTitle(title);
        conversation.setTitleCustomized(true);
        conversation.setUpdatedAt(LocalDateTime.now());
        return ConversationResponse.from(conversation);
    }

    // Every conversation operation validates both conversation id and current MySQL user id.
    private Conversation requireOwnedConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation does not exist."));
        if (!conversation.getUserId().equals(userId)) {
            throw new ForbiddenException("Conversation does not belong to current user.");
        }
        return conversation;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new BadRequestException("limit must be greater than 0.");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private ConversationMessageRole parseRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new BadRequestException("role is required.");
        }
        try {
            return ConversationMessageRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported role: " + role);
        }
    }

    private String normalizeMessageContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BadRequestException("content must not be blank.");
        }
        String normalized = content.trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("content is too long.");
        }
        return normalized;
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BadRequestException("title must not be blank.");
        }
        String normalized = title.replaceAll("\\s+", " ").trim();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("title is too long.");
        }
        return normalized;
    }

    private void updateDefaultTitleAfterFirstAssistantMessage(
            Conversation conversation,
            ConversationMessageRole role,
            String assistantContent
    ) {
        if (role != ConversationMessageRole.ASSISTANT) {
            return;
        }
        if (conversation.isTitleCustomized() || !Conversation.DEFAULT_TITLE.equals(conversation.getTitle())) {
            return;
        }
        if (messageRepository.countByConversation_IdAndRole(conversation.getId(), ConversationMessageRole.ASSISTANT) != 1) {
            return;
        }

        String source = messageRepository
                .findFirstByConversation_IdAndRoleOrderByCreatedAtAsc(conversation.getId(), ConversationMessageRole.USER)
                .map(ConversationMessage::getContent)
                .orElse(assistantContent);
        conversation.setTitle(titleGenerator.generateTitle(source));
    }
}
