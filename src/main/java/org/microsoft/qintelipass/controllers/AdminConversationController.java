package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.response.AdminConversationDetailResponse;
import org.microsoft.qintelipass.response.AdminConversationSummaryResponse;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.services.ConversationService;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Audit-only API. Administrator membership comes from app.conversation.admin-user-ids,
 * never from a user-controlled request header.
 */
@RestController
@RequestMapping("api/v1/admin/conversations")
public class AdminConversationController {
    private final ConversationService conversationService;
    private final CurrentUserService currentUserService;

    public AdminConversationController(
            ConversationService conversationService,
            CurrentUserService currentUserService
    ) {
        this.conversationService = conversationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<List<AdminConversationSummaryResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        currentUserService.requireAdministrator(request);
        return ApiResponse.ok(conversationService.listConversationsForAdministrator(page, limit));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<AdminConversationDetailResponse> detail(
            @PathVariable Long conversationId,
            HttpServletRequest request
    ) {
        currentUserService.requireAdministrator(request);
        return ApiResponse.ok(conversationService.getConversationForAdministrator(conversationId));
    }
}
