package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.ITrafficStatService;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
public class UserController {
    private final UserService userService;
    private final ITrafficStatService trafficStatService;
    @Autowired
    public UserController(UserService userService, ITrafficStatService trafficStatService) {
        this.userService = userService;
        this.trafficStatService = trafficStatService;
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        List<User> allUsers = userService.getAllUsers();

        // 搜索过滤
        List<User> filteredUsers = allUsers;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            filteredUsers = allUsers.stream()
                .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(lowerKeyword))
                        || (u.getPhone() != null && u.getPhone().contains(keyword)))
                .collect(Collectors.toList());
        }

        // 分页
        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, filteredUsers.size());

        if (startIndex >= filteredUsers.size()) {
            startIndex = 0;
            endIndex = Math.min(size, filteredUsers.size());
        }

        List<User> pageUsers = filteredUsers.subList(startIndex, endIndex);

        // 返回格式：{ total: 100, items: [...] }
        Map<String, Object> response = new HashMap<>();
        response.put("total", filteredUsers.size());
        response.put("items", pageUsers);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> cancelUser(@PathVariable Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid user ID"));
        }
        
        boolean success = userService.deactivateUser(userId);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "User cancelled successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to cancel user. User may not exist or already be cancelled."));
    }
    
    /**
     * 获取当前用户信息（用于测试注销拦截）
     * 这个接口会被 UserStatusInterceptor 拦截
     */
    @GetMapping("/user/profile")
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
                "status", user.getStatus()
            )
        ));
    }

    @GetMapping("/users/active/statistics")
    public ResponseEntity<?> getActiveUsers(){
        Map<String, Object> stat = Map.of(
                "count", trafficStatService.getActiveUsers(),
                "percent", 0.1
        );
        return ResponseEntity.ok().body(stat);
    }

}
