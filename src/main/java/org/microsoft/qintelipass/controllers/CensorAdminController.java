//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.models.CensorKeyword;
import org.microsoft.qintelipass.services.CensorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/admin/censor"})
public class CensorAdminController {
    private final CensorService censorService;
    @Autowired
    public CensorAdminController(CensorService censorService) {
        this.censorService = censorService;
    }

    @GetMapping({"/keywords"})
    public ResponseEntity<?> listKeywords() {
        return ResponseEntity.ok(this.censorService.listKeywords());
    }

    @PostMapping({"/keywords"})
    public ResponseEntity<?> addKeyword(@RequestBody Map<String, String> request) {
        String keyword = (String)request.get("keyword");
        CensorKeyword savedKeyword = this.censorService.addKeyword(keyword);
        return ResponseEntity.ok(savedKeyword);
    }

    @PatchMapping({"/keywords/{id}/enabled"})
    public ResponseEntity<?> setKeywordEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        Boolean enabled = (Boolean)request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "enabled is required"));
        } else {
            CensorKeyword keyword = this.censorService.setKeywordEnabled(id, enabled);
            return ResponseEntity.ok(keyword);
        }
    }

    @GetMapping({"/records"})
    public ResponseEntity<?> listRecords() {
        return ResponseEntity.ok(this.censorService.listRecords());
    }
}
