package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.security.SecurityUtil;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
    @Autowired
    private TokenUsageService tokenUsageService;

    @PostMapping("/call")
    public ResponseEntity<ResponseBody<Map<String, Object>>> callAgent(
            @RequestParam(value = "modelId", defaultValue = "1") Long modelId) {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
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

        tokenUsageService.recordTokenUsage(userId, modelId, mockToken);

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
