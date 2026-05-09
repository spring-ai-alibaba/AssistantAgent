package com.alibaba.assistant.agent.management.controller;

import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.management.internal.SkillPackageParser;
import com.alibaba.assistant.agent.management.model.ExperienceVO;
import com.alibaba.assistant.agent.management.model.SkillPackage;
import com.alibaba.assistant.agent.management.model.SkillPackageImportResult;
import com.alibaba.assistant.agent.management.spi.SkillExchangeService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/exp-console/api/skills")
public class SkillExchangeController {

    private final SkillExchangeService service;
    private final SkillPackageParser packageParser;

    public SkillExchangeController(SkillExchangeService service, SkillPackageParser packageParser) {
        this.service = service;
        this.packageParser = packageParser;
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, String>> importSkill(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String id = service.importSkill(content);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PostMapping("/preview")
    public ResponseEntity<ExperienceVO> preview(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        ExperienceVO vo = service.previewSkillImport(content);
        return ResponseEntity.ok(vo);
    }

    @PostMapping("/import-package")
    public ResponseEntity<SkillPackageImportResult> importPackage(@RequestParam("file") MultipartFile file) throws IOException {
        SkillPackage pkg = packageParser.parseAuto(file.getInputStream());
        SkillPackageImportResult result = service.importSkillPackage(pkg);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/preview-package")
    public ResponseEntity<SkillPackageImportResult> previewPackage(@RequestParam("file") MultipartFile file) throws IOException {
        SkillPackage pkg = packageParser.parseAuto(file.getInputStream());
        SkillPackageImportResult result = service.previewSkillPackageImport(pkg);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/export/{id}")
    public ResponseEntity<Map<String, String>> exportSkill(@PathVariable("id") String id) {
        String content = service.exportSkill(id);
        return ResponseEntity.ok(Map.of("content", content));
    }

    @GetMapping("/export-package/{id}")
    public ResponseEntity<byte[]> exportSkillPackage(@PathVariable("id") String id) {
        byte[] zipBytes = service.exportSkillPackage(id);
        String filename = sanitizeFilename(id) + ".zip";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(
                org.springframework.http.ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build());
        headers.setContentLength(zipBytes.length);
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }

    private static String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "skill";
        }
        String trimmed = value.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, String>> exportAll(@RequestParam(name = "type") ExperienceType type) {
        String content = service.exportAllSkills(type);
        return ResponseEntity.ok(Map.of("content", content));
    }
}
