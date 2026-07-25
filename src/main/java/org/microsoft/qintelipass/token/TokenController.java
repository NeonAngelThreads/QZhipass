package org.microsoft.qintelipass.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
public class TokenController {

    @Autowired
    private TokenService tokenService;

    // ============ 用户视角 ============

    /**
     * 获取当前用户当日 token 限额与使用情况（前端左上角用户信息板块调用）
     */
    @GetMapping("/api/user/token")
    public ResponseEntity<?> getUserToken(
            @RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        Long userId = parseUserId(userIdStr);
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Missing or invalid X-User-Id header"));
        }
        UserTokenStatus status = tokenService.getDailyStatus(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", status));
    }

    // ============ 前端适配：用户 Token 用量（employee-token-stats 页面） ============

    /**
     * GET /api/v1/user/token/usage
     * 前端 employee-token-stats 页面期望的格式
     */
    @GetMapping("/api/v1/user/token/usage")
    public ResponseEntity<?> getUserTokenUsage(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Missing X-User-Id header"));
        }
        UserTokenStatus status = tokenService.getDailyStatus(userId);

        String st = status.overQuota() ? "over" :
                    (status.quota() > 0 && (double) status.used() / status.quota() > 0.85) ? "warning" : "normal";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("daily_limit", status.quota());
        data.put("used_today", status.used());
        data.put("remaining", status.remaining());
        data.put("status", st);
        return ResponseEntity.ok(Map.of("success", true, "rawData", data));
    }

    /**
     * GET /api/v1/user/token/weekly
     * 本周每日消耗趋势 + 月统计
     */
    @GetMapping("/api/v1/user/token/weekly")
    public ResponseEntity<?> getUserWeeklyTrend(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Missing X-User-Id header"));
        }
        Map<String, Object> data = tokenService.getUserWeeklyTrend(userId);
        return ResponseEntity.ok(Map.of("success", true, "rawData", data));
    }

    /**
     * GET /api/v1/user/token/conversations
     * 最近对话 Token 消耗记录
     */
    @GetMapping("/api/v1/user/token/conversations")
    public ResponseEntity<?> getRecentConversations(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Missing X-User-Id header"));
        }
        List<Map<String, Object>> list = tokenService.getRecentConversations(userId);
        return ResponseEntity.ok(Map.of("success", true, "rawData", list));
    }

    // ============ 管理员视角 ============

    /** 可视化看板数据 */
    @GetMapping("/api/admin/token/dashboard")
    public ResponseEntity<?> getDashboard() {
        DashboardData data = tokenService.getDashboard();
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /** 按部门统计的当日 token 使用表 */
    @GetMapping("/api/admin/token/usage")
    public ResponseEntity<?> getDepartmentUsage() {
        DepartmentUsageData data = tokenService.getDepartmentUsage();
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /** 统一设置所有用户的 token 限额（保存后立即生效） */
    @PostMapping("/api/admin/token/quota")
    public ResponseEntity<?> setQuota(@RequestBody Map<String, Object> body) {
        Object quotaObj = body.get("quota");
        if (quotaObj == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "quota is required"));
        }
        long quota;
        try {
            quota = Long.parseLong(String.valueOf(quotaObj));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "quota must be a number"));
        }
        try {
            tokenService.setGlobalQuota(quota);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Quota updated", "quota", quota));
    }

    // ============ 前端适配：管理员仪表盘 + 配额（admin-token-dashboard 页面） ============

    /**
     * GET /api/v1/admin/token/dashboard
     * 前端 admin-token-dashboard 页面期望的 KPI + 图表 + 员工列表格式
     */
    @GetMapping("/api/v1/admin/token/dashboard")
    public ResponseEntity<?> getDashboardForFrontend() {
        try {
            Map<String, Object> rawData = tokenService.getDashboardForFrontend();
            return ResponseEntity.ok(Map.of("success", true, "rawData", rawData));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PUT /api/v1/admin/token/quota
     * 前端期望的参数名是 daily_token_limit
     */
    @PutMapping("/api/v1/admin/token/quota")
    public ResponseEntity<?> setQuotaForFrontend(@RequestBody Map<String, Object> body) {
        Object limitObj = body.get("daily_token_limit");
        if (limitObj == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "daily_token_limit is required"));
        }
        long newLimit = Long.parseLong(String.valueOf(limitObj));
        if (newLimit < 1000) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Token limit must be >= 1000"));
        }
        tokenService.setGlobalQuota(newLimit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("daily_limit", newLimit);
        result.put("affectedUsers", countTotalUsers());
        return ResponseEntity.ok(Map.of("success", true, "rawData", result));
    }

    /**
     * PUT /api/v1/admin/token/quota/{userId}
     * 管理员单独调整某用户的配额
     */
    @PutMapping("/api/v1/admin/token/quota/{userId}")
    public ResponseEntity<?> setUserQuotaForFrontend(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        Object limitObj = body.get("daily_token_limit");
        if (limitObj == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "daily_token_limit is required"));
        }
        long newLimit = Long.parseLong(String.valueOf(limitObj));
        if (newLimit < 1000) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Token limit must be >= 1000"));
        }
        tokenService.setUserQuota(userId, newLimit);
        return ResponseEntity.ok(Map.of("success", true, "message", "User quota updated"));
    }

    private long countTotalUsers() {
        try { return tokenService.countActiveUsers(); } catch (Exception e) { return 0; }
    }

    // ============ 对话（聊天）场景 ============

    /**
     * 每次发起对话框前检测是否超额。
     * 请求体：{ "userId": 1, "model": "gpt-4o", "estimatedTokens": 2000 }
     */
    @PostMapping("/v1/chat/check")
    public ResponseEntity<?> checkChat(@RequestBody Map<String, Object> body) {
        Long userId = parseUserId(body.get("userId"));
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "userId is required"));
        }
        String model = body.get("model") == null ? "default" : String.valueOf(body.get("model"));
        long estimated = toLong(body.get("estimatedTokens"), 2000L);

        boolean allowed = tokenService.checkQuota(userId, estimated);
        UserTokenStatus status = tokenService.getDailyStatus(userId);
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

    /**
     * 记录一次对话实际产生的 token 消耗（用于更新员工每日用量）。
     * 请求体：{ "userId": 1, "model": "gpt-4o", "promptTokens": 800, "completionTokens": 1200 }
     */
    @PostMapping("/v1/chat/usage")
    public ResponseEntity<?> recordChat(@RequestBody Map<String, Object> body) {
        Long userId = parseUserId(body.get("userId"));
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "userId is required"));
        }
        String model = body.get("model") == null ? "default" : String.valueOf(body.get("model"));
        long prompt = toLong(body.get("promptTokens"), 0L);
        long completion = toLong(body.get("completionTokens"), 0L);

        tokenService.recordUsage(userId, model, prompt, completion);
        UserTokenStatus status = tokenService.getDailyStatus(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", status));
    }

    // ============ 工具方法 ============

    private Long parseUserId(String userIdStr) {
        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(userIdStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseUserId(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(obj).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long toLong(Object obj, long def) {
        if (obj == null) {
            return def;
        }
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
