package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.request.AgentInvokeRequest;
import org.microsoft.qintelipass.request.CreateConversationRequest;
import org.microsoft.qintelipass.request.SaveConversationMessageRequest;
import org.microsoft.qintelipass.request.UpdateConversationModelRequest;
import org.microsoft.qintelipass.request.UpdateConversationTitleRequest;
import org.microsoft.qintelipass.response.*;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.services.CensorService;
import org.microsoft.qintelipass.services.AgentManagementService;
import org.microsoft.qintelipass.services.AIChatService;
import org.microsoft.qintelipass.services.AIModelProviderService;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

@RestController
@RequestMapping("api/v1/conversations")
// Controller only resolves the current user and request body; ownership is enforced in the service.
public class ConversationController {
    private final ConversationService conversationService;
    private final CurrentUserService currentUserService;
    private final CensorService censorService;
    private final UserService userService;
    private final AgentManagementService agentManagementService;
    private final AIChatService aiChatService;

    public ConversationController(ConversationService conversationService,
                                  CurrentUserService currentUserService,
                                  CensorService censorService,
                                  UserService userService,
                                  AgentManagementService agentManagementService,
                                  AIChatService aiChatService) {
        this.conversationService = conversationService;
        this.currentUserService = currentUserService;
        this.censorService = censorService;
        this.userService = userService;
        this.agentManagementService = agentManagementService;
        this.aiChatService = aiChatService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @RequestBody(required = false) CreateConversationRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        ConversationResponse response = conversationService.createConversation(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Conversation created.", response));
    }

    @PostMapping("/initial")
    public ResponseEntity<ApiResponse<ConversationResponse>> createInitialConversation(HttpServletRequest request) {
        Long userId = currentUserService.requireUserId(request);
        ConversationResponse response = conversationService.createInitialConversation(userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Initial conversation created.", response));
    }

    @GetMapping
    public ApiResponse<List<ConversationSummaryResponse>> listRecentConversations(
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        Long userId = currentUserService.requireUserId(request);
        return ApiResponse.ok(conversationService.listRecentConversations(userId, limit));
    }

    @PostMapping("/invoke-agent")
    public ApiResponse<AgentInvokeResponse> invokeAgent(
            @Valid @RequestBody AgentInvokeRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        Agent agent = agentManagementService.requireInvokableAgent(userId, request.getAgentId());

        Long conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = conversationService.createConversation(userId, null).conversationId();
        }

        ConversationDetailResponse detail = conversationService.getConversation(userId, conversationId);
        List<Message> history = detail.messages().stream()
                .map(message -> AIChatService.buildMessage(message.role(), message.content()))
                .toList();
        String modelKey = detail.conversation().modelKey();
        if (modelKey == null || modelKey.isBlank()) {
            modelKey = AIModelProviderService.DEFAULT_MODEL_KEY;
        }

        SaveConversationMessageRequest userMessage = new SaveConversationMessageRequest();
        userMessage.setRole("USER");
        userMessage.setContent(request.getMessage().trim());
        userMessage.setModelKey(modelKey);
        conversationService.saveMessage(userId, conversationId, userMessage);

        String answer = aiChatService.chat(
                modelKey,
                agent.getPrompt(),
                history,
                request.getMessage().trim(),
                null,
                null
        );

        SaveConversationMessageRequest assistantMessage = new SaveConversationMessageRequest();
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent(answer);
        assistantMessage.setModelKey(modelKey);
        ConversationMessageResponse saved = conversationService.saveMessage(
                userId, conversationId, assistantMessage);

        return ApiResponse.ok(new AgentInvokeResponse(
                conversationId,
                saved.id(),
                agent.getId(),
                agent.getName(),
                saved.content(),
                saved.createdAt()
        ));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailResponse> getConversation(
            @PathVariable Long conversationId,
            HttpServletRequest request
    ) {
        Long userId = currentUserService.requireUserId(request);
        return ApiResponse.ok(conversationService.getConversation(userId, conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<ConversationMessageResponse>> saveMessage(
            @PathVariable Long conversationId,
            @RequestBody SaveConversationMessageRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        ConversationMessageResponse response = conversationService.saveMessage(userId, conversationId, request);

        // Safe fallback: run sensitive-word check on request content if available
        try {
            User user = userService.getUserById(userId);
            if (user != null) {
                String inputContent = request != null ? request.getContent() : "";
                String outputContent = response.content() != null ? response.content() : "";
                censorService.checkAndRecord(
                        userId,
                        user.getName(),
                        user.getPhone(),
                        user.getDepartment() != null ? user.getDepartment() : "",
                        response.modelKey() != null ? response.modelKey() : "",
                        inputContent,
                        outputContent
                );
            }
        } catch (Exception ignored) {
            // never fail the message-save flow because of censor
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Message saved.", response));
    }

    @PatchMapping("/{conversationId}/model")
    public ApiResponse<ConversationResponse> updateModel(
            @PathVariable Long conversationId,
            @RequestBody UpdateConversationModelRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        return ApiResponse.ok(conversationService.updateModel(userId, conversationId, request));
    }

    @PatchMapping("/{conversationId}/title")
    public ApiResponse<ConversationResponse> updateTitle(
            @PathVariable Long conversationId,
            @RequestBody UpdateConversationTitleRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        return ApiResponse.ok(conversationService.updateTitle(userId, conversationId, request));
    }
}
