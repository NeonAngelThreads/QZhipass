package org.microsoft.qintelipass.controllers;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ai.token.DashboardData;
import org.microsoft.qintelipass.ai.token.DepartmentUsageData;
import org.microsoft.qintelipass.ai.token.UserTokenStatus;
import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.dtos.response.ResponseBody;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.scheduler.tasks.DailyAggregationTask;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/token")
public class TokenUsageController {
    @Autowired
    private TokenUsageService tokenUsageService;
    @Autowired
    private UserService userService;

    @GetMapping("/usage")
    public ResponseEntity<ResponseBody<UserTokenUsageDTO>> getUserUsage() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("Getting token usage for authenticated user: {}", userId);

        User user = requireUser(userId);
        UserTokenUsageDTO usage = tokenUsageService.getUserTokenUsage(user);
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

        User user = requireUser(userId);
        boolean canProceed = tokenUsageService.checkTokenLimit(user);
        UserTokenUsageDTO usage = tokenUsageService.getUserTokenUsage(user);

        return ResponseEntity.ok(ResponseBody.<Map<String, Object>>builder()
                .success(canProceed)
                .message(canProceed ? "Under token limit" : "Token limit exceeded")
                .payload(Map.of(
                        "canProceed", canProceed,
                        "usage", usage
                ))
                .build());
    }

    // ==================== 兼容对方版本：用户视角 ====================

    @GetMapping
    public ResponseEntity<?> getUserTokenByHeader() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        UserTokenStatus status = tokenUsageService.getDailyStatus(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", status));
    }

    @GetMapping("/user/usage")
    public ResponseEntity<?> getUserTokenUsageForFrontend() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();

        UserTokenStatus status = tokenUsageService.getDailyStatus(userId);
        String state = status.overQuota()
                ? "over"
                : status.quota() > 0 && (double) status.used() / status.quota() > 0.85
                        ? "warning"
                        : "normal";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("daily_limit", status.quota());
        data.put("used_today", status.used());
        data.put("remaining", status.remaining());
        data.put("status", state);
        data.put("department", status.department());
        data.put("name", status.userName());

        return ResponseEntity.ok(Map.of("success", true, "rawData", data));
    }

    @GetMapping("/user/weekly")
    public ResponseEntity<?> getUserWeeklyTrend() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();

        Map<String, Object> data = tokenUsageService.getUserWeeklyTrend(userId);
        return ResponseEntity.ok(Map.of("success", true, "rawData", data));
    }

    @GetMapping("/user/conversations")
    public ResponseEntity<?> getRecentConversations() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();

        List<Map<String, Object>> data = tokenUsageService.getRecentConversations(userId);
        return ResponseEntity.ok(Map.of("success", true, "rawData", data));
    }

    private User requireUser(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new NotFoundException("User not found: " + userId);
        }
        return user;
    }
}

@Slf4j
@RestController
@RequestMapping
class TokenUsageLegacyUserController {
    @Autowired
    private TokenUsageService tokenUsageService;

    @GetMapping("/api/user/token")
    public ResponseEntity<?> getLegacyUserToken() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        UserTokenStatus status = tokenUsageService.getDailyStatus(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", status));
    }

    @GetMapping("/api/v1/user/token/usage")
    public ResponseEntity<?> getLegacyUserTokenUsage() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        UserTokenStatus status = tokenUsageService.getDailyStatus(userId);
        String state = status.overQuota()
                ? "over"
                : status.quota() > 0 && (double) status.used() / status.quota() > 0.85
                        ? "warning"
                        : "normal";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("daily_limit", status.quota());
        data.put("used_today", status.used());
        data.put("remaining", status.remaining());
        data.put("status", state);
        data.put("department", status.department());
        data.put("name", status.userName());
        return ResponseEntity.ok(Map.of("success", true, "rawData", data));
    }

    @GetMapping("/api/v1/user/token/weekly")
    public ResponseEntity<?> getLegacyUserWeekly() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        Map<String, Object> data = tokenUsageService.getUserWeeklyTrend(userId);
        return ResponseEntity.ok(Map.of("success", true, "rawData", data));
    }

    @GetMapping("/api/v1/user/token/conversations")
    public ResponseEntity<?> getLegacyUserConversations() {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();
        List<Map<String, Object>> data = tokenUsageService.getRecentConversations(userId);
        return ResponseEntity.ok(Map.of("success", true, "rawData", data));
    }
}

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/token")
class TokenUsageAdminController {
    @Autowired
    private TokenUsageService tokenUsageService;
    @Autowired
    private UserService userService;

    @GetMapping("/rank")
    public ResponseEntity<ResponseBody<List<TokenUsageRankDTO>>> getDailyRank(
            @RequestParam(value = "topN", defaultValue = "10") int topN) {
        SecurityUtil.requireAdmin();
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
        SecurityUtil.requireAdmin();
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

        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.badRequest().body(
                    ResponseBody.<Void>builder()
                                    .success(false)
                                    .message("User not found")
                                    .build()
            );
        }
        tokenUsageService.setUserTokenLimit(user, limit);
        return ResponseEntity.ok(ResponseBody.<Void>builder()
                .success(true)
                .message("Token limit updated successfully")
                .build());
    }

    @GetMapping("/statistics/total/consumption")
    public ResponseEntity<?> tokenStat() {
        SecurityUtil.requireAdmin();
        Map<String, Object> stat = Map.of(
                "tokens", tokenUsageService.getTodayTotalTokens()
        );
        return ResponseEntity.ok().body(stat);
    }

    @GetMapping("/statistics/overuse/users")
    public ResponseEntity<?> overuseUsers() {
        SecurityUtil.requireAdmin();
        Long users = tokenUsageService.getOveruseUsers();
        Map<String, Object> stat = Map.of(
                "count", users,
                "percent", (double) users / (double) DailyAggregationTask.getYesterdayOveruseUsers()
        );
        return ResponseEntity.ok().body(stat);
    }

    @GetMapping("/statistics/models")
    public ResponseEntity<ResponseBody<Map<String, Object>>> tokensForModels() {
        SecurityUtil.requireAdmin();
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
        SecurityUtil.requireAdmin();
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
        SecurityUtil.requireAdmin();
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
        SecurityUtil.requireAdmin();
        log.info("Getting all user token usage requested by admin user: {}", SecurityUtil.getCurrentUserId());

        List<Map<String, Object>> usageList = tokenUsageService.getAllUserTokenUsage();
        return ResponseEntity.ok(ResponseBody.<List<Map<String, Object>>>builder()
                .success(true)
                .message("All user token usage retrieved successfully")
                .payload(usageList)
                .build());
    }

    // ==================== 兼容对方版本：管理员前端接口 ====================

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardForFrontend() {
        SecurityUtil.requireAdmin();
        try {
            Map<String, Object> rawData = tokenUsageService.getDashboardForFrontend();
            return ResponseEntity.ok(Map.of("success", true, "rawData", rawData));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", safeMessage(exception)));
        }
    }

    @GetMapping("/usage")
    public ResponseEntity<?> getUsageForFrontend() {
        SecurityUtil.requireAdmin();
        try {
            DepartmentUsageData data = tokenUsageService.getDepartmentUsage();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userUsageRows", data.users());
            result.put("departmentRows", data.departments());
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", safeMessage(exception)));
        }
    }

    @PutMapping("/quota")
    public ResponseEntity<?> setQuotaForFrontend(@RequestBody Map<String, Object> body) {
        SecurityUtil.requireAdmin();
        Long newLimit = parseLong(body.get("daily_token_limit"));
        if (newLimit == null) {
            return badRequest("daily_token_limit is required and must be a number");
        }
        if (newLimit < 1_000) {
            return badRequest("Token limit must be >= 1000");
        }

        tokenUsageService.setGlobalQuota(newLimit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("daily_limit", newLimit);
        result.put("affectedUsers", tokenUsageService.countActiveUsers());
        return ResponseEntity.ok(Map.of("success", true, "rawData", result));
    }

    @PutMapping("/quota/{userId}")
    public ResponseEntity<?> setUserQuotaForFrontend(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body
    ) {
        SecurityUtil.requireAdmin();
        Long newLimit = parseLong(body.get("daily_token_limit"));
        if (newLimit == null) {
            return badRequest("daily_token_limit is required and must be a number");
        }
        if (newLimit < 1_000) {
            return badRequest("Token limit must be >= 1000");
        }

        tokenUsageService.setUserQuota(userId, newLimit);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User quota updated"
        ));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", message));
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Token service request failed"
                : message;
    }
}

@Slf4j
@RestController
@RequestMapping
class TokenUsageLegacyAdminController {
    @Autowired
    private TokenUsageService tokenUsageService;

    @GetMapping("/api/admin/token/dashboard")
    public ResponseEntity<?> getLegacyDashboard() {
        SecurityUtil.requireAdmin();
        DashboardData data = tokenUsageService.getDashboard();
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/api/admin/token/usage")
    public ResponseEntity<?> getLegacyDepartmentUsage() {
        SecurityUtil.requireAdmin();
        DepartmentUsageData data = tokenUsageService.getDepartmentUsage();
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @PostMapping("/api/admin/token/quota")
    public ResponseEntity<?> setLegacyQuota(@RequestBody Map<String, Object> body) {
        SecurityUtil.requireAdmin();
        Long quota = parseLong(body.get("quota"));
        if (quota == null) {
            return badRequest("quota is required and must be a number");
        }

        try {
            tokenUsageService.setGlobalQuota(quota);
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Quota updated",
                "quota", quota
        ));
    }

    @GetMapping("/api/v1/admin/token/dashboard")
    public ResponseEntity<?> getLegacyDashboardForFrontend() {
        SecurityUtil.requireAdmin();
        try {
            Map<String, Object> rawData = tokenUsageService.getDashboardForFrontend();
            return ResponseEntity.ok(Map.of("success", true, "rawData", rawData));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", safeMessage(exception)));
        }
    }

    @GetMapping("/api/v1/admin/token/usage")
    public ResponseEntity<?> getLegacyUsageForFrontend() {
        SecurityUtil.requireAdmin();
        try {
            DepartmentUsageData data = tokenUsageService.getDepartmentUsage();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userUsageRows", data.users());
            result.put("departmentRows", data.departments());
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", safeMessage(exception)));
        }
    }

    @PutMapping("/api/v1/admin/token/quota")
    public ResponseEntity<?> setLegacyQuotaForFrontend(@RequestBody Map<String, Object> body) {
        SecurityUtil.requireAdmin();
        Long newLimit = parseLong(body.get("daily_token_limit"));
        if (newLimit == null) {
            return badRequest("daily_token_limit is required and must be a number");
        }
        if (newLimit < 1_000) {
            return badRequest("Token limit must be >= 1000");
        }

        tokenUsageService.setGlobalQuota(newLimit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("daily_limit", newLimit);
        result.put("affectedUsers", tokenUsageService.countActiveUsers());
        return ResponseEntity.ok(Map.of("success", true, "rawData", result));
    }

    @PutMapping("/api/v1/admin/token/quota/{userId}")
    public ResponseEntity<?> setLegacyUserQuotaForFrontend(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body
    ) {
        SecurityUtil.requireAdmin();
        Long newLimit = parseLong(body.get("daily_token_limit"));
        if (newLimit == null) {
            return badRequest("daily_token_limit is required and must be a number");
        }
        if (newLimit < 1_000) {
            return badRequest("Token limit must be >= 1000");
        }

        tokenUsageService.setUserQuota(userId, newLimit);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User quota updated"
        ));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", message));
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Token service request failed"
                : message;
    }
}

@Slf4j
@RestController
@RequestMapping
class TokenUsageChatController {
    @Autowired
    private TokenUsageService tokenUsageService;

    @PostMapping({"/v1/chat/check", "/api/v1/chat/check"})
    public ResponseEntity<?> checkChat(@RequestBody Map<String, Object> body) {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();

        String model = body.get("model") == null
                ? "default"
                : String.valueOf(body.get("model"));
        long estimatedTokens = parseLongOrDefault(body.get("estimatedTokens"), 2_000L);

        boolean allowed = tokenUsageService.checkQuota(userId, estimatedTokens);
        UserTokenStatus status = tokenUsageService.getDailyStatus(userId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "allowed", allowed,
                "model", model,
                "quota", status.quota(),
                "used", status.used(),
                "remaining", status.remaining(),
                "overQuota", status.overQuota()
        ));
    }

    @PostMapping({"/v1/chat/usage", "/api/v1/chat/usage"})
    public ResponseEntity<?> recordChat(@RequestBody Map<String, Object> body) {
        SecurityUtil.requireAuthentication();
        Long userId = SecurityUtil.getCurrentUserId();

        String model = body.get("model") == null
                ? "default"
                : String.valueOf(body.get("model"));
        long promptTokens = parseLongOrDefault(body.get("promptTokens"), 0L);
        long completionTokens = parseLongOrDefault(body.get("completionTokens"), 0L);

        try {
            tokenUsageService.recordUsage(userId, model, promptTokens, completionTokens);
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }

        UserTokenStatus status = tokenUsageService.getDailyStatus(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", status));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", message));
    }

    private long parseLongOrDefault(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
