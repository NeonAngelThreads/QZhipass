package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.entity.CensorKeyword;
import org.microsoft.qintelipass.services.CensorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/censor")
public class CensorAdminController {

    private final CensorService censorService;

    public CensorAdminController(CensorService censorService) {
        this.censorService = censorService;
    }

    @GetMapping("/keywords")
    public ResponseEntity<?> listKeywords() {
        return ResponseEntity.ok(censorService.listKeywords());
    }

    @PostMapping("/keywords")
    public ResponseEntity<?> addKeyword(@RequestBody Map<String, String> request) {
        String keyword = request.get("keyword");
        CensorKeyword savedKeyword = censorService.addKeyword(keyword);
        return ResponseEntity.ok(savedKeyword);
    }

    @PatchMapping("/keywords/{id}/enabled")
    public ResponseEntity<?> setKeywordEnabled(@PathVariable Long id,
                                               @RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");

        if (enabled == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "enabled is required")
            );
        }

        CensorKeyword keyword = censorService.setKeywordEnabled(id, enabled);
        return ResponseEntity.ok(keyword);
    }

    @GetMapping("/records")
    public ResponseEntity<?> listRecords() {
        return ResponseEntity.ok(censorService.listRecords());
    }
}