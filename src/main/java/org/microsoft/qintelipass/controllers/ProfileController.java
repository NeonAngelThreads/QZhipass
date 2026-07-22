package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class ProfileController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 获取当前用户信息
     * 这个接口会被 UserStatusInterceptor 拦截
     * 如果用户状态是 CANCELLED，拦截器会返回 403 + USER_CANCELLED
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(@RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing X-User-Id header"));
        }
        
        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid user ID format"));
        }
        
        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
        }
        
        // 返回用户信息（会被 UserStatusInterceptor 拦截如果状态是 CANCELLED）
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "phone", user.getPhone(),
                "status", user.getStatus(),
                "wechat", user.getWechat()
            )
        ));
    }
}
