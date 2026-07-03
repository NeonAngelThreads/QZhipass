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
// 模型接口入口：先确认当前登录用户，再返回可用模型列表。
public class ModelController {
    private final AiModelService aiModelService;
    private final CurrentUserService currentUserService;

    public ModelController(AiModelService aiModelService, CurrentUserService currentUserService) {
        this.aiModelService = aiModelService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/available")
    // 前端输入模型选择时调用该接口，只暴露 enabled=true 的模型配置。
    public ApiResponse<List<ModelResponse>> listAvailableModels(HttpServletRequest request) {
        String userId = currentUserService.requireUserId(request);
        return ApiResponse.ok(aiModelService.listAvailableModels(userId));
    }
}
