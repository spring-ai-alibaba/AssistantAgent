package com.alibaba.assistant.agent.management.controller;

import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.management.model.ExperienceCreateRequest;
import com.alibaba.assistant.agent.management.model.ExperienceListQuery;
import com.alibaba.assistant.agent.management.model.ExperienceUpdateRequest;
import com.alibaba.assistant.agent.management.model.ExperienceVO;
import com.alibaba.assistant.agent.management.model.PageResult;
import com.alibaba.assistant.agent.management.spi.ExperienceManagementService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/exp-console/api/experiences")
public class ExperienceManagementController {

    private static final String GLOBAL_TENANT_ID = "global";

    private final ExperienceManagementService service;

    public ExperienceManagementController(ExperienceManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "type", required = false) ExperienceType type,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "includeGlobal", defaultValue = "true") boolean includeGlobal,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        ExperienceListQuery query = new ExperienceListQuery();
        query.setType(type);
        query.setKeyword(keyword);
        query.setTenantId(tenantId);
        query.setIncludeGlobal(includeGlobal);
        query.setPage(page);
        query.setSize(size);
        PageResult<ExperienceVO> result = service.list(query);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", result.getData());
        body.put("total", result.getTotal());
        body.put("page", result.getPage());
        body.put("size", result.getSize());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "type", required = false) ExperienceType type,
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "includeGlobal", defaultValue = "true") boolean includeGlobal,
            @RequestParam(name = "topK", defaultValue = "10") int topK) {
        List<ExperienceVO> results = service.search(q, type, topK);
        if (tenantId != null && !tenantId.isBlank()) {
            results = results.stream()
                    .filter(Objects::nonNull)
                    .filter(vo -> matchesTenantScope(vo, tenantId, includeGlobal))
                    .collect(Collectors.toList());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", results);
        body.put("total", results.size());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<ExperienceType, Long>> stats(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "includeGlobal", defaultValue = "true") boolean includeGlobal) {
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.ok(service.countByType());
        }
        Map<ExperienceType, Long> counts = new LinkedHashMap<>();
        for (ExperienceType type : ExperienceType.values()) {
            ExperienceListQuery query = new ExperienceListQuery();
            query.setType(type);
            query.setTenantId(tenantId);
            query.setIncludeGlobal(includeGlobal);
            query.setPage(1);
            query.setSize(Integer.MAX_VALUE);
            counts.put(type, service.list(query).getTotal());
        }
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExperienceVO> getById(@PathVariable("id") String id) {
        ExperienceVO vo = service.getById(id);
        if (vo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(vo);
    }

    /**
     * 返回指定 asset 的原始内容（文本或 base64），供管理后台 UI 懒加载。
     * 路径支持多级，如 /{id}/assets/scripts/cmd.sh。
     */
    @GetMapping("/{id}/assets/**")
    public ResponseEntity<Map<String, Object>> getAssetContent(
            @PathVariable("id") String id,
            jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        int p = uri.indexOf("/assets/");
        if (p < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid path"));
        }
        String assetPath = uri.substring(p + "/assets/".length());
        com.alibaba.assistant.agent.extension.experience.model.AssetEntry match =
                service.loadAsset(id, assetPath);
        if (match == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", match.getPath());
        body.put("mediaType", match.getMediaType());
        body.put("role", match.getRole());
        body.put("size", match.getSize());
        body.put("content", match.getContent());
        body.put("contentRef", match.getContentRef());
        return ResponseEntity.ok(body);
    }

    /**
     * 重新生成指定经验下所有 reference 的 description（通常在 LLM summarizer 不可用时先导入、
     * 事后配置好 ChatModel 之后再批量补齐）。
     */
    @PostMapping("/{id}/resummarize")
    public ResponseEntity<Map<String, Object>> resummarize(@PathVariable("id") String id) {
        int updated = service.resummarizeReferences(id);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody ExperienceCreateRequest request) {
        String id = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> update(
            @PathVariable("id") String id,
            @RequestBody ExperienceUpdateRequest request) {
        service.update(id, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable("id") String id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private boolean matchesTenantScope(ExperienceVO vo, String tenantId, boolean includeGlobal) {
        String normalizedTenantId = tenantId.trim();
        List<String> tenantIds = vo.getTenantIdList();
        boolean isGlobal = tenantIds == null
                || tenantIds.isEmpty()
                || tenantIds.stream().anyMatch(id -> GLOBAL_TENANT_ID.equalsIgnoreCase(id));
        if (GLOBAL_TENANT_ID.equalsIgnoreCase(normalizedTenantId)) {
            return isGlobal;
        }
        if (isGlobal) {
            return includeGlobal;
        }
        return tenantIds.contains(normalizedTenantId);
    }
}
