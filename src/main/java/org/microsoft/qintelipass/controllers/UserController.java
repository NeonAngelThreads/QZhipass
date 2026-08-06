package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.ITrafficStatService;
import org.microsoft.qintelipass.dtos.UserFreezeLogDTO;
import org.microsoft.qintelipass.dtos.request.FreezeUserRequest;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.services.UserFreezeService;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.security.SecurityUtil;
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
    private final UserFreezeService userFreezeService;

    public UserController(
            UserService userService,
            ITrafficStatService trafficStatService,
            UserFreezeService userFreezeService
    ) {
        this.userService = userService;
        this.trafficStatService = trafficStatService;
        this.userFreezeService = userFreezeService;
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        SecurityUtil.requireAdmin();
        List<User> allUsers = userService.getAllUsers();
        List<User> filteredUsers = allUsers;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            filteredUsers = allUsers.stream()
                    .filter(user -> (user.getName() != null && user.getName().toLowerCase().contains(lowerKeyword))
                            || (user.getPhone() != null && user.getPhone().contains(keyword)))
                    .collect(Collectors.toList());
        }

        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, filteredUsers.size());
        if (startIndex >= filteredUsers.size()) {
            startIndex = 0;
            endIndex = Math.min(size, filteredUsers.size());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("total", filteredUsers.size());
        response.put("items", filteredUsers.subList(startIndex, endIndex));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> cancelUser(@PathVariable Long userId) {
        SecurityUtil.requireAdmin();
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid user ID"));
        }

        boolean success = userService.deactivateUser(userId);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "User cancelled successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "success",
                false,
                "message",
                "Failed to cancel user. User may not exist or already be cancelled."
        ));
    }

    @PostMapping("/users/{userId}/freeze")
    public ResponseEntity<?> freezeUser(
            @PathVariable Long userId,
            @RequestBody FreezeUserRequest request
    ) {
        SecurityUtil.requireAdmin();
        try {
            UserFreezeLogDTO log = userFreezeService.freezeUser(
                    userId,
                    request == null ? null : request.getReason(),
                    request == null ? null : request.getCensorAlertId(),
                    SecurityUtil.getCurrentUserId(),
                    SecurityUtil.getCurrentUsername()
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "User frozen successfully",
                    "data", log
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", exception.getMessage()
            ));
        }
    }

    @PostMapping("/users/{userId}/unfreeze")
    public ResponseEntity<?> unfreezeUser(
            @PathVariable Long userId,
            @RequestBody FreezeUserRequest request
    ) {
        SecurityUtil.requireAdmin();
        try {
            UserFreezeLogDTO log = userFreezeService.unfreezeUser(
                    userId,
                    request == null ? null : request.getReason(),
                    SecurityUtil.getCurrentUserId(),
                    SecurityUtil.getCurrentUsername()
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "User unfrozen successfully",
                    "data", log
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", exception.getMessage()
            ));
        }
    }

    @GetMapping("/users/{userId}/freeze-logs")
    public ResponseEntity<?> getFreezeLogs(@PathVariable Long userId) {
        SecurityUtil.requireAdmin();
        return ResponseEntity.ok(Map.of(
                "success",
                true,
                "data",
                userFreezeService.getFreezeLogs(userId)
        ));
    }

    @GetMapping("/user/profile")
    public ResponseEntity<?> getUserProfile(@RequestParam Long userId) {
        SecurityUtil.requireAdmin();

        User user = userService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
        }

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
    public ResponseEntity<?> getActiveUsers() {
        SecurityUtil.requireAdmin();
        Map<String, Object> stat = Map.of(
                "count", trafficStatService.getActiveUsers(),
                "percent", 0.1
        );
        return ResponseEntity.ok().body(stat);
    }
}