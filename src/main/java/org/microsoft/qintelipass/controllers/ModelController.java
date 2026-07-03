package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.ModelResponse;
import org.microsoft.qintelipass.services.AiModelService;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/models")
// Returns enabled model configs for the current authenticated MySQL user id.
public class ModelController {
    private final AiModelService aiModelService;
    private final CurrentUserService currentUserService;

    public ModelController(AiModelService aiModelService, CurrentUserService currentUserService) {
        this.aiModelService = aiModelService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/available")
    public ApiResponse<List<ModelResponse>> listAvailableModels(HttpServletRequest request) {
        Long userId = currentUserService.requireUserId(request);
        return ApiResponse.ok(aiModelService.listAvailableModels(userId));
    }
}
