package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.request.CreateConversationRequest;
import org.microsoft.qintelipass.request.SaveConversationMessageRequest;
import org.microsoft.qintelipass.request.UpdateConversationModelRequest;
import org.microsoft.qintelipass.request.UpdateConversationTitleRequest;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.ConversationDetailResponse;
import org.microsoft.qintelipass.response.ConversationMessageResponse;
import org.microsoft.qintelipass.response.ConversationResponse;
import org.microsoft.qintelipass.response.ConversationSummaryResponse;
import org.microsoft.qintelipass.services.ConversationService;
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
// 对话接口入口：Controller 只解析当前用户和请求体，具体持久化与权限校验交给 Service。
public class ConversationController {
    private final ConversationService conversationService;
    private final CurrentUserService currentUserService;

    public ConversationController(ConversationService conversationService, CurrentUserService currentUserService) {
        this.conversationService = conversationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    // 手动创建新的空白对话，并返回前端跳转所需的 conversationId。
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @RequestBody(required = false) CreateConversationRequest request,
            HttpServletRequest httpRequest
    ) {
        String userId = currentUserService.requireUserId(httpRequest);
        ConversationResponse response = conversationService.createConversation(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Conversation created.", response));
    }

    @PostMapping("/initial")
    // 登录后也可显式补建初始对话，身份仍来自 accessToken。
    public ResponseEntity<ApiResponse<ConversationResponse>> createInitialConversation(HttpServletRequest request) {
        String userId = currentUserService.requireUserId(request);
        ConversationResponse response = conversationService.createInitialConversation(userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Initial conversation created.", response));
    }

    @GetMapping
    // 查询当前用户自己的最近对话列表，列表范围由 Service 按 userId 限定。
    public ApiResponse<List<ConversationSummaryResponse>> listRecentConversations(
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        String userId = currentUserService.requireUserId(request);
        return ApiResponse.ok(conversationService.listRecentConversations(userId, limit));
    }

    @GetMapping("/{conversationId}")
    // 读取单个对话详情时，Service 会校验 conversationId 是否属于当前用户。
    public ApiResponse<ConversationDetailResponse> getConversation(
            @PathVariable Long conversationId,
            HttpServletRequest request
    ) {
        String userId = currentUserService.requireUserId(request);
        return ApiResponse.ok(conversationService.getConversation(userId, conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    // 保存 USER、ASSISTANT 或 SYSTEM 消息，并同步更新对话活动时间。
    public ResponseEntity<ApiResponse<ConversationMessageResponse>> saveMessage(
            @PathVariable Long conversationId,
            @RequestBody SaveConversationMessageRequest request,
            HttpServletRequest httpRequest
    ) {
        String userId = currentUserService.requireUserId(httpRequest);
        ConversationMessageResponse response = conversationService.saveMessage(userId, conversationId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Message saved.", response));
    }

    @PatchMapping("/{conversationId}/model")
    // 切换对话模型前先解析当前用户，模型可用性与归属校验在 Service 中完成。
    public ApiResponse<ConversationResponse> updateModel(
            @PathVariable Long conversationId,
            @RequestBody UpdateConversationModelRequest request,
            HttpServletRequest httpRequest
    ) {
        String userId = currentUserService.requireUserId(httpRequest);
        return ApiResponse.ok(conversationService.updateModel(userId, conversationId, request));
    }

    @PatchMapping("/{conversationId}/title")
    // 用户手动改标题后会标记为自定义，避免后续自动标题覆盖。
    public ApiResponse<ConversationResponse> updateTitle(
            @PathVariable Long conversationId,
            @RequestBody UpdateConversationTitleRequest request,
            HttpServletRequest httpRequest
    ) {
        String userId = currentUserService.requireUserId(httpRequest);
        return ApiResponse.ok(conversationService.updateTitle(userId, conversationId, request));
    }
}
