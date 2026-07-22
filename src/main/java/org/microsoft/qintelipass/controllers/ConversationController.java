package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.request.CreateConversationRequest;
import org.microsoft.qintelipass.request.SaveConversationMessageRequest;
import org.microsoft.qintelipass.request.UpdateConversationModelRequest;
import org.microsoft.qintelipass.request.UpdateConversationTitleRequest;
import org.microsoft.qintelipass.response.*;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.services.CensorService;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/conversations")
// Controller only resolves the current user and request body; ownership is enforced in the service.
public class ConversationController {
    private final ConversationService conversationService;
    private final CurrentUserService currentUserService;
    private final CensorService censorService;
    private final UserService userService;

    public ConversationController(ConversationService conversationService,
                                  CurrentUserService currentUserService,
                                  CensorService censorService,
                                  UserService userService) {
        this.conversationService = conversationService;
        this.currentUserService = currentUserService;
        this.censorService = censorService;
        this.userService = userService;
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
