package org.microsoft.qintelipass.controllers;

import jakarta.persistence.criteria.Predicate;
import org.microsoft.qintelipass.dtos.CensorKeywordDTO;
import org.microsoft.qintelipass.entity.CensorKeyword;
import org.microsoft.qintelipass.repository.CensorKeywordRepository;
import org.microsoft.qintelipass.services.censor.CensorKeywordLoader;
import org.microsoft.qintelipass.util.security.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
            @RequestParam(value = "size", defaultValue = "1000") int size) {
        SecurityUtil.requireAdmin();
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

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 1000);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<CensorKeyword> result = censorKeywordRepository.findAll(spec, pageable);

        List<CensorKeywordDTO> items = result.getContent().stream().map(this::toDTO).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("total", result.getTotalElements());
        response.put("items", items);
        response.put("page", safePage);
        response.put("size", safeSize);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/censor/keywords
     * 新增敏感词
     */
    @PostMapping
    public ResponseEntity<?> createKeyword(@RequestBody CensorKeywordDTO dto) {
        SecurityUtil.requireAdmin();
        String keyword = normalize(dto.getKeyword());
        if (keyword == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Keyword must not be blank"));
        }
        if (censorKeywordRepository.existsByKeyword(keyword)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Keyword already exists"));
        }

        CensorKeyword entity = new CensorKeyword(keyword);
        entity.setCode(normalize(dto.getCode()));
        entity.setCategory(defaultIfBlank(dto.getCategory(), "OTHER"));
        entity.setRiskLevel(defaultIfBlank(dto.getRiskLevel(), "LOW"));
        if (dto.getEnabled() != null) entity.setEnabled(dto.isEnabled());
        entity = censorKeywordRepository.save(entity);
        censorKeywordLoader.refresh();
        return ResponseEntity.ok(Map.of("success", true, "data", toDTO(entity)));
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importKeywordsFromJson(@RequestBody Object body) {
        SecurityUtil.requireAdmin();
        List<CensorKeywordDTO> items = parseJsonImportItems(body);
        ImportResult result = importKeywords(items);
        return ResponseEntity.ok(result.toResponse());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importKeywordsFromFile(@RequestParam("file") MultipartFile file) {
        SecurityUtil.requireAdmin();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Import file must not be empty"));
        }

        try {
            ImportResult result = importKeywords(parseFileImportItems(file));
            return ResponseEntity.ok(result.toResponse());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to read import file"));
        }
    }

    /**
     * PUT /api/v1/admin/censor/keywords/{id}
     * 编辑敏感词
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateKeyword(@PathVariable Long id, @RequestBody CensorKeywordDTO dto) {
        SecurityUtil.requireAdmin();
        Optional<CensorKeyword> opt = censorKeywordRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "关键词不存在"));
        }
        CensorKeyword entity = opt.get();
        if (dto.getCode() != null) entity.setCode(normalize(dto.getCode()));
        if (dto.getKeyword() != null) {
            String keyword = normalize(dto.getKeyword());
            if (keyword == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Keyword must not be blank"));
            }
            entity.setKeyword(keyword);
        }
        if (dto.getCategory() != null) entity.setCategory(defaultIfBlank(dto.getCategory(), "OTHER"));
        if (dto.getRiskLevel() != null) entity.setRiskLevel(defaultIfBlank(dto.getRiskLevel(), "LOW"));
        if (dto.getEnabled() != null) entity.setEnabled(dto.isEnabled());
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
        SecurityUtil.requireAdmin();
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
        SecurityUtil.requireAdmin();
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
        SecurityUtil.requireAdmin();
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
        SecurityUtil.requireAdmin();
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : normalized;
    }

    private List<CensorKeywordDTO> parseJsonImportItems(Object body) {
        Object rawItems = body;
        if (body instanceof Map<?, ?> map) {
            rawItems = map.get("items");
            if (rawItems == null) {
                rawItems = map.get("keywords");
            }
        }

        List<CensorKeywordDTO> items = new ArrayList<>();
        if (rawItems instanceof Collection<?> collection) {
            for (Object item : collection) {
                CensorKeywordDTO dto = toImportDTO(item);
                if (dto != null) {
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private CensorKeywordDTO toImportDTO(Object item) {
        if (item instanceof String keyword) {
            CensorKeywordDTO dto = new CensorKeywordDTO();
            dto.setKeyword(keyword);
            return dto;
        }

        if (item instanceof Map<?, ?> map) {
            CensorKeywordDTO dto = new CensorKeywordDTO();
            dto.setCode(asString(map.get("code")));
            dto.setKeyword(asString(map.get("keyword")));
            dto.setCategory(asString(map.get("category")));
            dto.setRiskLevel(asString(map.get("riskLevel")));
            Object enabled = map.get("enabled");
            if (enabled instanceof Boolean value) {
                dto.setEnabled(value);
            }
            return dto;
        }

        return null;
    }

    private List<CensorKeywordDTO> parseFileImportItems(MultipartFile file) throws IOException {
        List<CensorKeywordDTO> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                String trimmed = normalize(line);
                if (trimmed == null) {
                    continue;
                }
                if (firstLine && looksLikeHeader(trimmed)) {
                    firstLine = false;
                    continue;
                }
                firstLine = false;

                String[] columns = trimmed.split(",", -1);
                CensorKeywordDTO dto = new CensorKeywordDTO();
                dto.setKeyword(columns.length > 0 ? columns[0] : null);
                dto.setCategory(columns.length > 1 ? columns[1] : null);
                dto.setRiskLevel(columns.length > 2 ? columns[2] : null);
                dto.setCode(columns.length > 3 ? columns[3] : null);
                items.add(dto);
            }
        }
        return items;
    }

    private boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("keyword") || lower.startsWith("word") || lower.startsWith("sensitive");
    }

    private ImportResult importKeywords(List<CensorKeywordDTO> items) {
        ImportResult result = new ImportResult();
        Set<String> seenInRequest = new HashSet<>();

        for (CensorKeywordDTO item : items) {
            String keyword = item == null ? null : normalize(item.getKeyword());
            if (keyword == null) {
                result.invalid++;
                continue;
            }

            if (!seenInRequest.add(keyword) || censorKeywordRepository.existsByKeyword(keyword)) {
                result.skipped++;
                result.skippedKeywords.add(keyword);
                continue;
            }

            CensorKeyword entity = new CensorKeyword(keyword);
            entity.setCode(normalize(item.getCode()));
            entity.setCategory(defaultIfBlank(item.getCategory(), "OTHER"));
            entity.setRiskLevel(defaultIfBlank(item.getRiskLevel(), "LOW"));
            entity.setEnabled(item.isEnabled());
            censorKeywordRepository.save(entity);
            result.imported++;
        }

        if (result.imported > 0) {
            censorKeywordLoader.refresh();
        }

        return result;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static class ImportResult {
        int imported;
        int skipped;
        int invalid;
        List<String> skippedKeywords = new ArrayList<>();

        Map<String, Object> toResponse() {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("imported", imported);
            response.put("skipped", skipped);
            response.put("invalid", invalid);
            response.put("skippedKeywords", skippedKeywords);
            return response;
        }
    }
}
