package org.microsoft.qintelipass.controllers;

import jakarta.persistence.criteria.Predicate;
import org.microsoft.qintelipass.dtos.CensorKeywordDTO;
import org.microsoft.qintelipass.entity.CensorKeyword;
import org.microsoft.qintelipass.repository.CensorKeywordRepository;
import org.microsoft.qintelipass.services.CensorKeywordLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/keywords")
public class CensorKeywordController {

    private final CensorKeywordRepository censorKeywordRepository;
    private final CensorKeywordLoader censorKeywordLoader;

    public CensorKeywordController(CensorKeywordRepository censorKeywordRepository,
                                   CensorKeywordLoader censorKeywordLoader) {
        this.censorKeywordRepository = censorKeywordRepository;
        this.censorKeywordLoader = censorKeywordLoader;
    }

    /**
     * GET /api/v1/admin/censor/keywords
     * 分页/筛选查询敏感词列表
     */
    @GetMapping
    public ResponseEntity<?> getKeywords(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Specification<CensorKeyword> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("keyword"), kw),
                        cb.like(root.get("code"), kw)
                ));
            }
            if (category != null && !category.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("category"), category.trim()));
            }
            if (riskLevel != null && !riskLevel.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel.trim()));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<CensorKeyword> result = censorKeywordRepository.findAll(spec, pageable);

        List<CensorKeywordDTO> items = result.getContent().stream().map(this::toDTO).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("total", result.getTotalElements());
        response.put("items", items);
        response.put("page", page);
        response.put("size", size);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/censor/keywords
     * 新增敏感词
     */
    @PostMapping
    public ResponseEntity<?> createKeyword(@RequestBody CensorKeywordDTO dto) {
        CensorKeyword entity = new CensorKeyword(dto.getKeyword());
        entity.setCode(dto.getCode());
        entity.setCategory(dto.getCategory());
        entity.setRiskLevel(dto.getRiskLevel());
        entity.setEnabled(dto.isEnabled());
        entity = censorKeywordRepository.save(entity);
        censorKeywordLoader.refresh();
        return ResponseEntity.ok(Map.of("success", true, "data", toDTO(entity)));
    }

    /**
     * PUT /api/v1/admin/censor/keywords/{id}
     * 编辑敏感词
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateKeyword(@PathVariable Long id, @RequestBody CensorKeywordDTO dto) {
        Optional<CensorKeyword> opt = censorKeywordRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "关键词不存在"));
        }
        CensorKeyword entity = opt.get();
        if (dto.getCode() != null) entity.setCode(dto.getCode());
        if (dto.getKeyword() != null) entity.setKeyword(dto.getKeyword());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getRiskLevel() != null) entity.setRiskLevel(dto.getRiskLevel());
        entity.setEnabled(dto.isEnabled());
        entity = censorKeywordRepository.save(entity);
        censorKeywordLoader.refresh();
        return ResponseEntity.ok(Map.of("success", true, "data", toDTO(entity)));
    }

    /**
     * PATCH /api/v1/admin/censor/keywords/{id}/enabled
     * 启用/停用敏感词
     */
    @PutMapping("/{id}/enabled")
    public ResponseEntity<?> toggleEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Optional<CensorKeyword> opt = censorKeywordRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "关键词不存在"));
        }
        CensorKeyword entity = opt.get();
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少 enabled 字段"));
        }
        entity.setEnabled(enabled);
        entity = censorKeywordRepository.save(entity);
        censorKeywordLoader.refresh();
        return ResponseEntity.ok(Map.of("success", true, "data", toDTO(entity)));
    }

    /**
     * DELETE /api/v1/admin/censor/keywords/{id}
     * 注销敏感词
     */
    @PutMapping("/{id}/enable")
    public ResponseEntity<?> enableKeyword(@PathVariable Long id) {
        Optional<CensorKeyword> opt = censorKeywordRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "关键词不存在"));
        }
        CensorKeyword entity = opt.get();
        entity.setEnabled(true);
        entity = censorKeywordRepository.save(entity);
        censorKeywordLoader.refresh();
        return ResponseEntity.ok(Map.of("success", true, "data", toDTO(entity)));
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<?> disableKeyword(@PathVariable Long id) {
        Optional<CensorKeyword> opt = censorKeywordRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "关键词不存在"));
        }
        CensorKeyword entity = opt.get();
        entity.setEnabled(false);
        entity = censorKeywordRepository.save(entity);
        censorKeywordLoader.refresh();
        return ResponseEntity.ok(Map.of("success", true, "data", toDTO(entity)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteKeyword(@PathVariable Long id) {
        if (!censorKeywordRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "关键词不存在"));
        }
        censorKeywordRepository.deleteById(id);
        censorKeywordLoader.refresh();
        return ResponseEntity.ok(Map.of("success", true, "message", "注销成功"));
    }

    // ========== helper ==========

    private CensorKeywordDTO toDTO(CensorKeyword entity) {
        CensorKeywordDTO dto = new CensorKeywordDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setKeyword(entity.getKeyword());
        dto.setCategory(entity.getCategory());
        dto.setRiskLevel(entity.getRiskLevel());
        dto.setEnabled(entity.isEnabled());
        dto.setTriggerCount(entity.getTriggerCount());
        if (entity.getCreatedAt() != null) dto.setCreatedAt(entity.getCreatedAt().toString());
        if (entity.getUpdatedAt() != null) dto.setUpdatedAt(entity.getUpdatedAt().toString());
        return dto;
    }
}