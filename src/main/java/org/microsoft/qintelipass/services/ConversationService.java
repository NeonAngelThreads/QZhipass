package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.entity.Conversation;
import org.microsoft.qintelipass.entity.ConversationMessage;
import org.microsoft.qintelipass.entity.ConversationMessageRole;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.ForbiddenException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
// 对话业务核心：负责创建对话、保存消息、生成标题、切换模型和校验归属。
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
    // 创建新的空白对话；可选 modelKey 会先经过可用性校验。
    public ConversationResponse createConversation(String userId, CreateConversationRequest request) {
        String modelKey = aiModelService.normalizeOptionalModelKey(request == null ? null : request.getModelKey());

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(Conversation.DEFAULT_TITLE);
        conversation.setModelKey(modelKey);
        conversation.setStatus(Conversation.STATUS_ACTIVE);

        return ConversationResponse.from(conversationRepository.save(conversation));
    }

    @Transactional
    // 登录成功后的初始对话不绑定模型，仍归属于当前登录用户。
    public ConversationResponse createInitialConversation(String userId) {
        return createConversation(userId, null);
    }

    @Transactional(readOnly = true)
    // 只查询当前用户的对话，并按最近活动时间倒序返回。
    public List<ConversationSummaryResponse> listRecentConversations(String userId, Integer limit) {
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
    // 读取详情前必须先确认 conversationId 属于当前 userId。
    public ConversationDetailResponse getConversation(String userId, Long conversationId) {
        Conversation conversation = requireOwnedConversation(userId, conversationId);
        List<ConversationMessageResponse> messages = messageRepository
                .findByConversation_IdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(ConversationMessageResponse::from)
                .toList();
        ModelResponse model = aiModelService.findAvailableModel(conversation.getModelKey()).orElse(null);
        return new ConversationDetailResponse(ConversationResponse.from(conversation), messages, model);
    }

    @Transactional
    // 保存 USER、ASSISTANT、SYSTEM 消息；消息归属由 conversationId + userId 校验保证。
    public ConversationMessageResponse saveMessage(
            String userId,
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
        // 保存消息后同步更新对话的最后活动时间。
        conversation.setUpdatedAt(now);
        conversation.setLastMessageAt(now);

        // 第一次保存 Assistant 消息时，必要时自动生成标题。
        updateDefaultTitleAfterFirstAssistantMessage(conversation, role, content);
        return ConversationMessageResponse.from(savedMessage);
    }

    @Transactional
    // 切换模型时只允许使用当前可用模型，并将选择持久化到对话。
    public ConversationResponse updateModel(
            String userId,
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
    // 用户主动修改标题后标记为自定义，后续自动标题逻辑不会覆盖。
    public ConversationResponse updateTitle(
            String userId,
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

    // 所有对话操作都需要同时校验对话编号和当前用户，防止越权访问。
    private Conversation requireOwnedConversation(String userId, Long conversationId) {
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
        // 只有仍处于默认标题且未手动改名的对话，才允许自动更新标题。
        if (conversation.isTitleCustomized() || !Conversation.DEFAULT_TITLE.equals(conversation.getTitle())) {
            return;
        }
        // 仅第一条 Assistant 消息触发标题生成，避免后续回复反复覆盖。
        if (messageRepository.countByConversation_IdAndRole(conversation.getId(), ConversationMessageRole.ASSISTANT) != 1) {
            return;
        }

        // 优先用第一条 USER 消息作为标题来源，没有用户消息时退回 Assistant 内容。
        String source = messageRepository
                .findFirstByConversation_IdAndRoleOrderByCreatedAtAsc(conversation.getId(), ConversationMessageRole.USER)
                .map(ConversationMessage::getContent)
                .orElse(assistantContent);
        conversation.setTitle(titleGenerator.generateTitle(source));
    }
}
