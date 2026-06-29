package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    @Autowired
    private UserService userService;
    
    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    @PostMapping("/users/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable String userId) {
        boolean success = userService.deactivateUser(userId);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "User deactivated successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to deactivate user"));
    }
}
