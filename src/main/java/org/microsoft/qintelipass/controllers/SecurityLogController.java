package org.microsoft.qintelipass.controllers;

import org.microsoft.qintelipass.dtos.CensorRecordDTO;
import org.microsoft.qintelipass.dtos.response.ApiResponse;
import org.microsoft.qintelipass.services.censor.CensorService;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security-logs")
public class SecurityLogController {

    private final CensorService censorService;

    public SecurityLogController(CensorService censorService) {
        this.censorService = censorService;
    }

    @GetMapping
    public ApiResponse<?> listRecords(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SecurityUtil.requireAdmin();
        Page<CensorRecordDTO> result = (q != null && !q.isBlank())
                ? censorService.searchRecords(q.trim(), page, size)
                : censorService.listAllRecords(page, size);
        return ApiResponse.ok(result);
    }
}