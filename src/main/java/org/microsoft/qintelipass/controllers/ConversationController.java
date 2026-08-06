package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.request.*;
import org.microsoft.qintelipass.dtos.response.*;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.services.censor.CensorService;
import org.microsoft.qintelipass.services.chat.ConversationService;
import org.microsoft.qintelipass.services.chat.ConversationTurnService;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("api/v1/conversations")
// Controller only resolves the current user and request body; ownership is enforced in the service.
public class ConversationController {
    private final ConversationService conversationService;
    private final CensorService censorService;
    private final UserService userService;
    private final ConversationTurnService conversationTurnService;

    public ConversationController(ConversationService conversationService,
                                  CensorService censorService,
                                  UserService userService,
                                  ConversationTurnService conversationTurnService) {
        this.conversationService = conversationService;
        this.censorService = censorService;
        this.userService = userService;
        this.conversationTurnService = conversationTurnService;
    }

    @PostMapping("/{conversationId}/turns")
    public ResponseEntity<ApiResponse<ConversationTurnResponse>> sendTurn(
            @PathVariable Long conversationId,
            @Valid @RequestBody ConversationTurnRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        User user = userService.getUserById(userId);
        ConversationTurnResponse response = conversationTurnService.send(user, conversationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Turn completed.", response));
    }

    @PostMapping("/turns")
    public ResponseEntity<ApiResponse<ConversationTurnResponse>> sendFirstTurn(
            @Valid @RequestBody ConversationTurnRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        User user = userService.getUserById(userId);
        ConversationTurnResponse response = conversationTurnService.sendNew(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Conversation created and turn completed.", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @RequestBody(required = false) CreateConversationRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        ConversationResponse response = conversationService.createConversation(userService.getUserById(userId), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Conversation created.", response));
    }

    @PostMapping("/initial")
    public ResponseEntity<ApiResponse<ConversationResponse>> createInitialConversation(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        User user = userService.getUserById(userId);
        ConversationResponse response = conversationService.createInitialConversation(user);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Initial conversation created.", response));
    }

    @GetMapping
    public ApiResponse<List<ConversationSummaryResponse>> listRecentConversations(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        User user = userService.getUserById(userId);
        return ApiResponse.ok(conversationService.listRecentConversations(userId, page, limit));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailResponse> getConversation(
            @PathVariable Long conversationId,
            HttpServletRequest request
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        User user = userService.getUserById(userId);
        return ApiResponse.ok(conversationService.getConversation(user, conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<ConversationMessageResponse>> saveMessage(
            @PathVariable Long conversationId,
            @RequestBody SaveConversationMessageRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        User user = userService.getUserById(userId);
        ConversationMessageResponse response = conversationService.saveMessage(user, conversationId, request);

        // Safe fallback: run sensitive-word check on request content if available
        try {
            if (user != null) {
                String inputContent = request != null ? request.getContent() : "";
                String outputContent = response.content() != null ? response.content() : "";
                censorService.checkAndRecord(
                        user,
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
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        User user = userService.getUserById(userId);
        return ApiResponse.ok(conversationService.updateModel(user, conversationId, request));
    }

    @PatchMapping("/{conversationId}/title")
    public ApiResponse<ConversationResponse> updateTitle(
            @PathVariable Long conversationId,
            @RequestBody UpdateConversationTitleRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("user id: {}", userId);
        User user = userService.getUserById(userId);
        return ApiResponse.ok(conversationService.updateTitle(user, conversationId, request));
    }
}

/**
 * Audit-only API. Administrator membership comes from app.conversation.admin-user-ids,
 * never from a user-controlled request header.
 */
@RestController
@RequestMapping("api/v1/admin/conversations")
class AdminConversationController {
    private final ConversationService conversationService;
    public AdminConversationController(
            ConversationService conversationService
    ) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ApiResponse<List<AdminConversationSummaryResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit
    ) {
        SecurityUtil.requireAdmin();
        return ApiResponse.ok(conversationService.listConversationsForAdministrator(page, limit));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<AdminConversationDetailResponse> detail(
            @PathVariable Long conversationId
    ) {
        SecurityUtil.requireAdmin();
        return ApiResponse.ok(conversationService.getConversationForAdministrator(conversationId));
    }
}