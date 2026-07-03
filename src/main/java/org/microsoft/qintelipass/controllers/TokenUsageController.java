package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.response.ResponseBody;
import org.microsoft.qintelipass.security.SecurityUtil;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/token")
public class TokenUsageController {
    @Autowired
    private TokenUsageService tokenUsageService;

    @GetMapping("/usage")
    public ResponseEntity<ResponseBody<UserTokenUsageDTO>> getUserUsage() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("Getting token usage for authenticated user: {}", userId);

        UserTokenUsageDTO usage = tokenUsageService.getUserTokenUsage(userId);
        return ResponseEntity.ok(ResponseBody.<UserTokenUsageDTO>builder()
                .success(true)
                .message("Token usage retrieved successfully")
                .payload(usage)
                .build());
    }

    @GetMapping("/check")
    public ResponseEntity<ResponseBody<Map<String, Object>>> checkLimit() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("Checking token limit for authenticated user: {}", userId);

        boolean canProceed = tokenUsageService.checkTokenLimit(userId);
        UserTokenUsageDTO usage = tokenUsageService.getUserTokenUsage(userId);

        return ResponseEntity.ok(ResponseBody.<Map<String, Object>>builder()
                .success(canProceed)
                .message(canProceed ? "Under token limit" : "Token limit exceeded")
                .payload(Map.of(
                        "canProceed", canProceed,
                        "usage", usage
                ))
                .build());
    }
}

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/token")
class TokenUsageAdminController {
    @Autowired
    private TokenUsageService tokenUsageService;
    @GetMapping("/rank")
    public ResponseEntity<ResponseBody<List<TokenUsageRankDTO>>> getDailyRank(
            @RequestParam(value = "topN", defaultValue = "10") int topN) {
        SecurityUtil.requireAuthentication();
        log.info("Getting daily token rank requested by admin user: {}", SecurityUtil.getCurrentUserId());

        if (topN <= 0 || topN > 100) {
            topN = 10;
        }

        List<TokenUsageRankDTO> rank = tokenUsageService.getDailyTokenRank(topN);
        return ResponseEntity.ok(ResponseBody.<List<TokenUsageRankDTO>>builder()
                .success(true)
                .message("Daily token usage rank retrieved successfully")
                .payload(rank)
                .build());
    }

    @PutMapping("/limit/{userId}")
    public ResponseEntity<ResponseBody<Void>> setUserLimit(
            @PathVariable Long userId,
            @RequestBody Map<String, Long> requestBody) {
        SecurityUtil.requireAuthentication();
        Long adminUserId = SecurityUtil.getCurrentUserId();
        log.info("Admin {} setting token limit for user: {}", adminUserId, userId);

        Long limit = requestBody.get("limit");
        if (limit == null || limit < 0) {
            return ResponseEntity.badRequest().body(
                    ResponseBody.<Void>builder()
                            .success(false)
                            .message("Invalid token limit")
                            .build()
            );
        }

        tokenUsageService.setUserTokenLimit(userId, limit);
        return ResponseEntity.ok(ResponseBody.<Void>builder()
                .success(true)
                .message("Token limit updated successfully")
                .build());
    }

    @GetMapping("/statistics/total/consumption")
    public ResponseEntity<?> tokenStat(){
        SecurityUtil.requireAuthentication();
        Map<String, Object> stat = Map.of(
                "tokens", tokenUsageService.getTodayTotalTokens()
        );
        return ResponseEntity.ok().body(stat);
    }
    @GetMapping("/statistics/overuse/users")
    public ResponseEntity<?> overuseUsers(){
        SecurityUtil.requireAuthentication();
        Map<String, Object> stat = Map.of(
                "count", tokenUsageService.getOveruseUsers(),
                "percent", 0.1
        );
        return ResponseEntity.ok().body(stat);
    }

    @GetMapping("/statistics/models")
    public ResponseEntity<ResponseBody<Map<String, Object>>> tokensForModels() {
        SecurityUtil.requireAuthentication();
        log.info("Getting model statistics requested by admin user: {}", SecurityUtil.getCurrentUserId());

        Map<String, Object> statistics = tokenUsageService.getModelStatisticsForLast7Days();
        return ResponseEntity.ok(ResponseBody.<Map<String, Object>>builder()
                .success(true)
                .message("Model statistics retrieved successfully")
                .payload(statistics)
                .build());
    }

    @GetMapping("/statistics/active/users")
    public ResponseEntity<ResponseBody<Map<String, Object>>> getActiveUsers() {
        SecurityUtil.requireAuthentication();
        log.info("Getting active users count requested by admin user: {}", SecurityUtil.getCurrentUserId());

        Long count = tokenUsageService.getActiveUserCount();
        return ResponseEntity.ok(ResponseBody.<Map<String, Object>>builder()
                .success(true)
                .message("Active users count retrieved successfully")
                .payload(Map.of("count", count))
                .build());
    }

    @GetMapping("/statistics/department")
    public ResponseEntity<ResponseBody<Map<String, Object>>> getDepartmentStatistics() {
        SecurityUtil.requireAuthentication();
        log.info("Getting department statistics requested by admin user: {}", SecurityUtil.getCurrentUserId());

        Map<String, Object> statistics = tokenUsageService.getDepartmentStatistics();
        return ResponseEntity.ok(ResponseBody.<Map<String, Object>>builder()
                .success(true)
                .message("Department statistics retrieved successfully")
                .payload(statistics)
                .build());
    }

    @GetMapping("/statistics/users")
    public ResponseEntity<ResponseBody<List<Map<String, Object>>>> getAllUserTokenUsage() {
        SecurityUtil.requireAuthentication();
        log.info("Getting all user token usage requested by admin user: {}", SecurityUtil.getCurrentUserId());

        List<Map<String, Object>> usageList = tokenUsageService.getAllUserTokenUsage();
        return ResponseEntity.ok(ResponseBody.<List<Map<String, Object>>>builder()
                .success(true)
                .message("All user token usage retrieved successfully")
                .payload(usageList)
                .build());
    }
}
