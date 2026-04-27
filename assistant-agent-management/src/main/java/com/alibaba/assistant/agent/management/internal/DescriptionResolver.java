package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.management.spi.ReferenceSummarizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 为 reference/asset 条目生成 description 的工具：
 * <ol>
 *     <li>.md 首个非空 H1 {@code # 标题}</li>
 *     <li>YAML frontmatter 中的 {@code description:} 字段（仅 .md 起始为 {@code ---} 时）</li>
 *     <li>注入的 {@link ReferenceSummarizer} — 典型为 LLM 总结</li>
 *     <li>回退：{@code "(no description) " + path}</li>
 * </ol>
 *
 * <p>同时负责计算 SHA-256 {@code contentHash}，供导入器复用已有 description 缓存。
 */
public final class DescriptionResolver {

    private DescriptionResolver() {
    }

    public static String resolve(String path, String content, ReferenceSummarizer summarizer) {
        if (content != null && path != null && path.toLowerCase().endsWith(".md")) {
            String h1 = extractH1(content);
            if (h1 != null && !h1.isBlank()) {
                return h1.trim();
            }
            String fm = extractFrontmatterDescription(content);
            if (fm != null && !fm.isBlank()) {
                return fm.trim();
            }
        }
        if (summarizer != null && content != null && !content.isBlank()) {
            try {
                String summary = summarizer.summarize(path, content);
                if (summary != null && !summary.isBlank()) {
                    return summary.trim();
                }
            } catch (RuntimeException ignored) {
                // 总结失败时走回退文案
            }
        }
        return "(no description) " + (path != null ? path : "");
    }

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(String text) {
        return text != null ? sha256Hex(text.getBytes(StandardCharsets.UTF_8)) : null;
    }

    private static String extractH1(String markdown) {
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                String title = trimmed.substring(2).trim();
                if (!title.isEmpty()) {
                    return title;
                }
            }
            // 跳过 frontmatter 之前的空行；遇到非空非 H1 且非 frontmatter 时继续扫描
        }
        return null;
    }

    private static String extractFrontmatterDescription(String markdown) {
        if (!markdown.startsWith("---")) {
            return null;
        }
        int end = markdown.indexOf("---", 3);
        if (end < 0) {
            return null;
        }
        String frontmatter = markdown.substring(3, end);
        for (String line : frontmatter.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("description:")) {
                String value = trimmed.substring("description:".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }
}
