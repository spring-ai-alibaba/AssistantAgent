package com.alibaba.assistant.agent.management.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析后的 Skill 包结构。
 *
 * <p>表示一个从 tgz/zip/文件夹解析出来的完整 skill 包，
 * 对齐 agentskills.io 标准的目录结构：
 * <pre>
 * skill-name/
 * ├── SKILL.md              ← 必须
 * ├── package.json          ← 可选（npm-style 元数据）
 * ├── scripts/              ← 可选（可执行脚本）
 * ├── references/           ← 可选（参考文档）
 * └── assets/               ← 可选（静态资源）
 * </pre>
 */
public class SkillPackage {

    /**
     * skill 包名称（来自 package.json 或 SKILL.md frontmatter）
     */
    private String name;

    /**
     * skill 版本号（来自 package.json 或 SKILL.md frontmatter）
     */
    private String version;

    /**
     * skill 描述（来自 package.json 或 SKILL.md frontmatter）
     */
    private String description;

    /**
     * SKILL.md 文件完整文本内容
     */
    private String skillMdContent;

    /**
     * 脚本文件映射：相对路径 → 文本内容（如 scripts/helper.py → "..."）
     */
    private Map<String, String> scripts = new LinkedHashMap<>();

    /**
     * 其他附件文件映射：相对路径 → 二进制内容（如 assets/template.json）
     */
    private Map<String, byte[]> otherFiles = new LinkedHashMap<>();

    /**
     * package.json 中的完整元数据（原始 JSON 解析后）
     */
    private Map<String, Object> packageMetadata = new LinkedHashMap<>();

    /**
     * 包中所有文件的路径列表（用于展示和审计）
     */
    private List<String> allFilePaths = new ArrayList<>();

    public SkillPackage() {
    }

    // --- Getters and Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSkillMdContent() {
        return skillMdContent;
    }

    public void setSkillMdContent(String skillMdContent) {
        this.skillMdContent = skillMdContent;
    }

    public Map<String, String> getScripts() {
        return scripts;
    }

    public void setScripts(Map<String, String> scripts) {
        this.scripts = scripts != null ? scripts : new LinkedHashMap<>();
    }

    public Map<String, byte[]> getOtherFiles() {
        return otherFiles;
    }

    public void setOtherFiles(Map<String, byte[]> otherFiles) {
        this.otherFiles = otherFiles != null ? otherFiles : new LinkedHashMap<>();
    }

    public Map<String, Object> getPackageMetadata() {
        return packageMetadata;
    }

    public void setPackageMetadata(Map<String, Object> packageMetadata) {
        this.packageMetadata = packageMetadata != null ? packageMetadata : new LinkedHashMap<>();
    }

    public List<String> getAllFilePaths() {
        return allFilePaths;
    }

    public void setAllFilePaths(List<String> allFilePaths) {
        this.allFilePaths = allFilePaths != null ? allFilePaths : new ArrayList<>();
    }

    public boolean hasSkillMd() {
        return skillMdContent != null && !skillMdContent.isBlank();
    }

    public boolean hasScripts() {
        return scripts != null && !scripts.isEmpty();
    }

    public boolean hasOtherFiles() {
        return otherFiles != null && !otherFiles.isEmpty();
    }
}
