package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.dtos.CensorAlertDTO;
import org.microsoft.qintelipass.dtos.CensorAlertRuleDTO;
import org.microsoft.qintelipass.dtos.response.ApiResponse;
import org.microsoft.qintelipass.services.censor.CensorAlertService;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/alerts")
public class CensorAlertController {

    private final CensorAlertService alertService;

    public CensorAlertController(CensorAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ApiResponse<Page<CensorAlertDTO>> listAlerts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SecurityUtil.requireAdmin();
        return ApiResponse.ok(alertService.listAlerts(q, department, status, from, to, page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        SecurityUtil.requireAdmin();
        return ApiResponse.ok(alertService.stats());
    }

    @GetMapping("/notifications")
    public ApiResponse<Map<String, Object>> notifications(@RequestParam(defaultValue = "3") int limit) {
        SecurityUtil.requireAdmin();
        return ApiResponse.ok(alertService.notifications(limit));
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<CensorAlertDTO> markHandled(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        SecurityUtil.requireAdmin();
        String handledBy = body == null ? null : body.get("handledBy");
        return ApiResponse.ok(alertService.markHandled(id, handledBy));
    }

    @GetMapping("/{id}")
    public ApiResponse<CensorAlertDTO> getAlert(@PathVariable Long id) {
        SecurityUtil.requireAdmin();
        return ApiResponse.ok(alertService.getAlert(id));
    }

    @GetMapping("/rules")
    public ApiResponse<List<CensorAlertRuleDTO>> listRules() {
        SecurityUtil.requireAdmin();
        return ApiResponse.ok(alertService.listRules());
    }

    @PostMapping("/rules")
    public ApiResponse<CensorAlertRuleDTO> createRule(@RequestBody CensorAlertRuleDTO dto) {
        SecurityUtil.requireAdmin();
        dto.setId(null);
        return ApiResponse.ok(alertService.saveRule(dto));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<CensorAlertRuleDTO> updateRule(@PathVariable Long id, @RequestBody CensorAlertRuleDTO dto) {
        SecurityUtil.requireAdmin();
        dto.setId(id);
        return ApiResponse.ok(alertService.saveRule(dto));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        SecurityUtil.requireAdmin();
        alertService.deleteRule(id);
        return ApiResponse.ok("Alert rule deleted", null);
    }
}
