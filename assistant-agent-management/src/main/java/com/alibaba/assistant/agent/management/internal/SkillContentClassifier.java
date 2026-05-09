package com.alibaba.assistant.agent.management.internal;

/**
 * 将 skill 包中的文件路径分类为 {@link com.alibaba.assistant.agent.extension.experience.model.ReferenceEntry
 * reference}（面向 LLM 的文档）或 {@link com.alibaba.assistant.agent.extension.experience.model.AssetEntry
 * asset}（仅在沙箱内使用的附件），并推导 asset 的角色标签。
 *
 * <p>规则对齐 {@code openspec/changes/progressive-experience-disclosure/design.md} 中的
 * Decision 2：
 * <ul>
 *     <li>{@code SKILL.md}：调用方已单独处理（写入 {@code Experience.content} + reference role=skill-md），
 *         不再经过本分类器。</li>
 *     <li>{@code references/**} 下任意文件 → reference</li>
 *     <li>{@code agents/**.md} → reference</li>
 *     <li>根目录的 {@code *.md}（除 {@code SKILL.md}、{@code README.md} 视情况）→ reference</li>
 *     <li>根目录的 {@code *.yaml}/{@code *.yml} → reference</li>
 *     <li>{@code scripts/**} → asset(role=script)</li>
 *     <li>{@code assets/**} → asset(role=asset)</li>
 *     <li>{@code evals/**} → asset(role=eval)</li>
 *     <li>{@code package.json} → asset(role=metadata)（同时由调用方解析元数据）</li>
 *     <li>其他（二进制、未知类型） → asset(role=asset)</li>
 * </ul>
 */
public final class SkillContentClassifier {

    private SkillContentClassifier() {
    }

    public enum Bucket {
        REFERENCE,
        ASSET
    }

    public static Bucket classify(String path) {
        String normalized = normalize(path);
        if (normalized.isEmpty()) {
            return Bucket.ASSET;
        }

        if (normalized.startsWith("references/")) {
            return Bucket.REFERENCE;
        }
        if (normalized.startsWith("scripts/")
                || normalized.startsWith("assets/")
                || normalized.startsWith("evals/")) {
            return Bucket.ASSET;
        }
        if (normalized.equals("package.json")) {
            return Bucket.ASSET;
        }
        if (normalized.startsWith("agents/") && normalized.endsWith(".md")) {
            return Bucket.REFERENCE;
        }
        // 根目录其他 md / yaml 文件视为 reference
        if (!normalized.contains("/")) {
            String lower = normalized.toLowerCase();
            if (lower.endsWith(".md") || lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                return Bucket.REFERENCE;
            }
        }
        return Bucket.ASSET;
    }

    public static String assetRoleFor(String path) {
        String normalized = normalize(path);
        if (normalized.equals("package.json")) {
            return "metadata";
        }
        if (normalized.startsWith("scripts/")) {
            return "script";
        }
        if (normalized.startsWith("evals/")) {
            return "eval";
        }
        if (normalized.startsWith("assets/")) {
            return "asset";
        }
        return "asset";
    }

    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String p = path.replace('\\', '/');
        if (p.startsWith("./")) {
            p = p.substring(2);
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }
}
