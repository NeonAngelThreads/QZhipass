package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.models.AiModelConfig;
import org.microsoft.qintelipass.request.CreateAgentRequest;
import org.microsoft.qintelipass.response.AgentDeleteCheckResponse;
import org.microsoft.qintelipass.response.AgentDeleteResultResponse;
import org.microsoft.qintelipass.response.AgentLibraryResponse;
import org.microsoft.qintelipass.response.AgentResponse;
import org.microsoft.qintelipass.response.AgentSummaryResponse;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.security.SecurityUtil;
import org.microsoft.qintelipass.services.AgentManagementService;
import org.microsoft.qintelipass.services.AiModelService;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping({"/api/v1/agent", "/api/v1/agents", "/api/agents"})
public class AgentController {
    @Autowired
    private TokenUsageService tokenUsageService;

    @Autowired
    private AiModelService aiModelService;

    private final AgentManagementService agentManagementService;
    private final CurrentUserService currentUserService;

    public AgentController(
            AgentManagementService agentManagementService,
            CurrentUserService currentUserService
    ) {
        this.agentManagementService = agentManagementService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ApiResponse<AgentResponse> create(
            @Valid @RequestBody CreateAgentRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        return ApiResponse.ok("创建成功", agentManagementService.create(userId, request));
    }

    @GetMapping
    public ApiResponse<List<AgentSummaryResponse>> list(
            @RequestParam(value = "q", required = false) String keyword,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(agentManagementService.listVisible(
                currentUserService.requireUserId(request), keyword));
    }

    @GetMapping("/mine")
    public ApiResponse<List<AgentResponse>> listPersonal(HttpServletRequest request) {
        return ApiResponse.ok(agentManagementService.listPersonal(
                currentUserService.requireUserId(request)));
    }

    @GetMapping("/catalog")
    public ApiResponse<List<AgentSummaryResponse>> catalog(
            @RequestParam(value = "q", required = false) String keyword,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(agentManagementService.listCatalog(
                currentUserService.requireUserId(request), keyword));
    }

    @GetMapping("/presets")
    public ApiResponse<List<AgentResponse>> listPresets() {
        return ApiResponse.ok(agentManagementService.listPresets());
    }

    @GetMapping("/count")
    public ApiResponse<Map<String, Object>> count(HttpServletRequest request) {
        long count = agentManagementService.count(currentUserService.requireUserId(request));
        return ApiResponse.ok(Map.of(
                "count", count,
                "maxLimit", AgentManagementService.MAX_PERSONAL_AGENTS
        ));
    }

    @PostMapping("/{agentId}/library")
    public ApiResponse<AgentLibraryResponse> addToLibrary(
            @PathVariable Long agentId,
            HttpServletRequest request
    ) {
        AgentLibraryResponse result = agentManagementService.addToLibrary(
                currentUserService.requireUserId(request), agentId);
        return ApiResponse.ok(result.alreadyAdded() ? "Agent已在用户库中" : "Agent添加成功", result);
    }

    @GetMapping({"/{agentId}/delete-check", "/{agentId}/delete-confirmation"})
    public ApiResponse<AgentDeleteCheckResponse> deleteCheck(
            @PathVariable Long agentId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(agentManagementService.getDeleteCheck(
                currentUserService.requireUserId(request), agentId));
    }

    @DeleteMapping("/{agentId}")
    public ApiResponse<AgentDeleteResultResponse> delete(
            @PathVariable Long agentId,
            HttpServletRequest request
    ) {
        AgentDeleteResultResponse result = agentManagementService.delete(
                currentUserService.requireUserId(request), agentId);
        return ApiResponse.ok(result.alreadyDeleted() ? "Agent已删除或移除" : "删除成功", result);
    }

    @PostMapping("/call")
    public ResponseEntity<ResponseBody<Map<String, Object>>> callAgent(
            @RequestParam(value = "modelId", required = true) Long modelId) {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        Optional<AiModelConfig> model = aiModelService.findModelById(modelId);
        if (model.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ResponseBody.<Map<String, Object>>builder()
                            .success(false)
                            .message("Could not find model with id " + modelId)
                            .build()
            );
        }

        AiModelConfig modelConfig = model.get();
        int mockToken = 10003;

        log.info("Agent call requested by authenticated user: {}, modelId: {}, estimated tokens: {}", userId, modelId, mockToken);
        tokenUsageService.increaseDailyTotalTokens(mockToken);
        boolean canProceed = tokenUsageService.checkTokenLimit(userId);
        if (!canProceed) {
            UserTokenUsageDTO usage = tokenUsageService.getUserTokenUsage(userId);
            return ResponseEntity.badRequest().body(
                    ResponseBody.<Map<String, Object>>builder()
                            .success(false)
                            .message(String.format("Token limit exceeded! Used: %d / %d",
                                    usage.getTokenUsed(), usage.getTokenLimit()))
                            .build()
            );
        }

        log.info("Agent call processing for user: {}", userId);

        tokenUsageService.recordTokenUsage(userId, modelConfig.getId(), mockToken);

        UserTokenUsageDTO updatedUsage = tokenUsageService.getUserTokenUsage(userId);

        Map<String, Object> result = Map.of(
                "response", "Agent response placeholder",
                "tokensUsed", mockToken,
                "currentUsage", updatedUsage
        );

        return ResponseEntity.ok(ResponseBody.<Map<String, Object>>builder()
                .success(true)
                .message("Agent call completed successfully")
                .payload(result)
                .build());
    }
}
