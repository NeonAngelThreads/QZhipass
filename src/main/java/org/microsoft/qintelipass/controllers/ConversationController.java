package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.microsoft.qintelipass.request.CreateConversationRequest;
import org.microsoft.qintelipass.request.ConversationTurnRequest;
import org.microsoft.qintelipass.request.SaveConversationMessageRequest;
import org.microsoft.qintelipass.request.UpdateConversationModelRequest;
import org.microsoft.qintelipass.request.UpdateConversationTitleRequest;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.ConversationDetailResponse;
import org.microsoft.qintelipass.response.ConversationMessageResponse;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ConversationSummaryResponse;
import org.microsoft.qintelipass.response.ConversationTurnResponse;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.ConversationTurnService;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/conversations")
// Controller only resolves the current user and request body; ownership is enforced in the service.
public class ConversationController {
    private final ConversationService conversationService;
    private final CurrentUserService currentUserService;
    private final ConversationTurnService conversationTurnService;

    public ConversationController(
            ConversationService conversationService,
            CurrentUserService currentUserService,
            ConversationTurnService conversationTurnService
    ) {
        this.conversationService = conversationService;
        this.currentUserService = currentUserService;
        this.conversationTurnService = conversationTurnService;
    }

    @PostMapping("/{conversationId}/turns")
    public ResponseEntity<ApiResponse<ConversationTurnResponse>> sendTurn(
            @PathVariable Long conversationId,
            @Valid @RequestBody ConversationTurnRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        ConversationTurnResponse response = conversationTurnService.send(userId, conversationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Turn completed.", response));
    }

    @PostMapping("/turns")
    public ResponseEntity<ApiResponse<ConversationTurnResponse>> sendFirstTurn(
            @Valid @RequestBody ConversationTurnRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        ConversationTurnResponse response = conversationTurnService.sendNew(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Conversation created and turn completed.", response));
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
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        Long userId = currentUserService.requireUserId(request);
        return ApiResponse.ok(conversationService.listRecentConversations(userId, page, limit));
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
