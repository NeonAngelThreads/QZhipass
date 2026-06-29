package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.services.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    @Autowired
    private RedisService redisService;
    
    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        return ResponseEntity.ok(Map.of("users", "user list"));
    }
    
    @PostMapping("/users/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable String userId) {
        return ResponseEntity.ok(Map.of("success", true, "message", "User deactivated"));
    }
}
