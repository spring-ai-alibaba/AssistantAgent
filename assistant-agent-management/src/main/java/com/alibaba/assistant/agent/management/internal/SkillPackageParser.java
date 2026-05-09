package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.management.model.SkillPackage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 包解析器。
 *
 * <p>支持从 tgz（gzipped tar）和 zip 格式的输入流解析出 {@link SkillPackage}。
 * 解析逻辑会识别包中的 SKILL.md、package.json、scripts/ 目录等文件，
 * 并将其映射到 SkillPackage 的对应字段。
 *
 * <p>tgz 包遵循 npm pack 的约定，所有文件位于 package/ 前缀下。
 */
public class SkillPackageParser {

    private static final Logger log = LoggerFactory.getLogger(SkillPackageParser.class);

    private static final String SKILL_MD = "SKILL.md";
    private static final String PACKAGE_JSON = "package.json";
    private static final String SCRIPTS_PREFIX = "scripts/";

    private static final int TAR_BLOCK_SIZE = 512;
    private static final long MAX_ENTRY_SIZE = 10 * 1024 * 1024; // 10MB per file
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 tgz（gzipped tar）输入流解析 skill 包
     */
    public SkillPackage parseTgz(InputStream tgzStream) throws IOException {
        try (GZIPInputStream gzipIn = new GZIPInputStream(new BufferedInputStream(tgzStream))) {
            return parseTar(gzipIn);
        }
    }

    /**
     * 从 zip 输入流解析 skill 包
     */
    public SkillPackage parseZip(InputStream zipStream) throws IOException {
        SkillPackage pkg = new SkillPackage();
        var allPaths = new ArrayList<String>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zipStream), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = normalizeEntryPath(entry.getName());
                allPaths.add(path);

                byte[] data = readBounded(zis, MAX_ENTRY_SIZE);
                classifyAndStore(pkg, path, data);
                zis.closeEntry();
            }
        }

        pkg.setAllFilePaths(allPaths);
        enrichFromPackageJson(pkg);

        log.debug("SkillPackageParser#parseZip - files={}, hasSkillMd={}", allPaths.size(), pkg.hasSkillMd());
        return pkg;
    }

    /**
     * 自动检测格式并解析（根据魔数判断 gzip 还是 zip）
     */
    public SkillPackage parseAuto(InputStream inputStream) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(inputStream, 4);
        bis.mark(4);
        int b0 = bis.read();
        int b1 = bis.read();
        bis.reset();

        // gzip magic: 0x1f 0x8b
        if (b0 == 0x1f && b1 == 0x8b) {
            return parseTgz(bis);
        }
        // zip magic: 0x50 0x4b (PK)
        if (b0 == 0x50 && b1 == 0x4b) {
            return parseZip(bis);
        }

        throw new IOException("Unsupported archive format: expected tgz or zip");
    }

    // ─── tar parsing (minimal, no external library) ────────────────────

    private SkillPackage parseTar(InputStream tarStream) throws IOException {
        SkillPackage pkg = new SkillPackage();
        var allPaths = new ArrayList<String>();

        byte[] headerBuf = new byte[TAR_BLOCK_SIZE];
        while (true) {
            int read = readFully(tarStream, headerBuf);
            if (read < TAR_BLOCK_SIZE || isEndOfArchive(headerBuf)) {
                break;
            }

            String rawName = extractString(headerBuf, 0, 100);
            // POSIX ustar prefix
            String prefix = extractString(headerBuf, 345, 155);
            String fullName = prefix.isEmpty() ? rawName : prefix + "/" + rawName;

            long size = parseOctal(headerBuf, 124, 12);
            byte typeFlag = headerBuf[156];

            // skip directories and special entries
            if (typeFlag == '5' || fullName.endsWith("/") || size == 0) {
                skipBlocks(tarStream, size);
                continue;
            }

            String path = normalizeEntryPath(fullName);
            allPaths.add(path);

            byte[] data = readTarEntry(tarStream, size);
            classifyAndStore(pkg, path, data);
        }

        pkg.setAllFilePaths(allPaths);
        enrichFromPackageJson(pkg);

        log.debug("SkillPackageParser#parseTar - files={}, hasSkillMd={}", allPaths.size(), pkg.hasSkillMd());
        return pkg;
    }

    private byte[] readTarEntry(InputStream in, long size) throws IOException {
        if (size > MAX_ENTRY_SIZE) {
            skipBlocks(in, size);
            return new byte[0];
        }
        byte[] data = new byte[(int) size];
        int offset = 0;
        while (offset < size) {
            int n = in.read(data, offset, (int) size - offset);
            if (n < 0) break;
            offset += n;
        }
        // skip padding to block boundary
        long remainder = size % TAR_BLOCK_SIZE;
        if (remainder != 0) {
            long padding = TAR_BLOCK_SIZE - remainder;
            in.skipNBytes(padding);
        }
        return data;
    }

    private void skipBlocks(InputStream in, long size) throws IOException {
        long totalToSkip = size;
        long remainder = size % TAR_BLOCK_SIZE;
        if (remainder != 0) {
            totalToSkip += (TAR_BLOCK_SIZE - remainder);
        }
        in.skipNBytes(totalToSkip);
    }

    private int readFully(InputStream in, byte[] buf) throws IOException {
        int offset = 0;
        while (offset < buf.length) {
            int n = in.read(buf, offset, buf.length - offset);
            if (n < 0) return offset;
            offset += n;
        }
        return offset;
    }

    private boolean isEndOfArchive(byte[] header) {
        for (int i = 0; i < TAR_BLOCK_SIZE; i++) {
            if (header[i] != 0) return false;
        }
        return true;
    }

    private String extractString(byte[] buf, int offset, int length) {
        int end = offset;
        while (end < offset + length && end < buf.length && buf[end] != 0) {
            end++;
        }
        return new String(buf, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private long parseOctal(byte[] buf, int offset, int length) {
        String s = extractString(buf, offset, length).trim();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ─── common helpers ────────────────────────────────────────────────

    /**
     * 规范化条目路径：去掉 npm pack 的 "package/" 前缀和其他常见包装前缀
     */
    private String normalizeEntryPath(String rawPath) {
        String path = rawPath.replace('\\', '/');
        // 去掉开头的 ./
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        // npm pack 约定：package/ 前缀
        if (path.startsWith("package/")) {
            path = path.substring("package/".length());
        }
        // 也处理单层目录前缀（如 skill-name/SKILL.md）
        if (!path.contains("/")) {
            return path;
        }
        // 如果第一段后直接就是 SKILL.md 等已知文件，则去掉第一段
        int firstSlash = path.indexOf('/');
        String afterFirst = path.substring(firstSlash + 1);
        if (afterFirst.equals(SKILL_MD) || afterFirst.equals(PACKAGE_JSON)
                || afterFirst.startsWith(SCRIPTS_PREFIX)
                || afterFirst.startsWith("references/") || afterFirst.startsWith("assets/")) {
            return afterFirst;
        }
        return path;
    }

    /**
     * 根据路径分类文件并存储到 SkillPackage 的对应字段
     */
    private void classifyAndStore(SkillPackage pkg, String path, byte[] data) {
        String fileName = getFileName(path);

        if (SKILL_MD.equals(fileName) || SKILL_MD.equalsIgnoreCase(fileName)) {
            pkg.setSkillMdContent(new String(data, StandardCharsets.UTF_8));
        } else if (PACKAGE_JSON.equals(fileName) && !path.contains("/")) {
            // 顶层 package.json
            pkg.getOtherFiles().put(path, data);
        } else if (path.startsWith(SCRIPTS_PREFIX) || path.startsWith("scripts\\")) {
            String content = new String(data, StandardCharsets.UTF_8);
            pkg.getScripts().put(path, content);
        } else {
            pkg.getOtherFiles().put(path, data);
        }
    }

    /**
     * 从 package.json 内容中提取元数据填充到 SkillPackage
     */
    private void enrichFromPackageJson(SkillPackage pkg) {
        byte[] pkgJsonData = pkg.getOtherFiles().get(PACKAGE_JSON);
        if (pkgJsonData == null) {
            return;
        }

        String json = new String(pkgJsonData, StandardCharsets.UTF_8);
        try {
            Map<String, Object> packageMetadata = objectMapper.readValue(json, new TypeReference<>() {
            });
            pkg.setPackageMetadata(packageMetadata);
        } catch (Exception e) {
            log.warn("SkillPackageParser#enrichFromPackageJson - failed to parse package.json as structured metadata", e);
        }
        // 简单的 JSON 字段提取（避免引入额外 JSON 库依赖）
        String name = extractJsonString(json, "name");
        String version = extractJsonString(json, "version");
        String description = extractJsonString(json, "description");

        if (name != null && (pkg.getName() == null || pkg.getName().isBlank())) {
            pkg.setName(name);
        }
        if (version != null && (pkg.getVersion() == null || pkg.getVersion().isBlank())) {
            pkg.setVersion(version);
        }
        if (description != null && (pkg.getDescription() == null || pkg.getDescription().isBlank())) {
            pkg.setDescription(description);
        }
    }

    /**
     * 从 JSON 字符串中提取指定 key 的 string 值（简单实现，避免额外依赖）
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;

        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;

        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return null;

        int quoteEnd = json.indexOf('"', quoteStart + 1);
        while (quoteEnd > 0 && json.charAt(quoteEnd - 1) == '\\') {
            quoteEnd = json.indexOf('"', quoteEnd + 1);
        }
        if (quoteEnd < 0) return null;

        return json.substring(quoteStart + 1, quoteEnd)
                .replace("\\\"", "\"")
                .replace("\\n", "\n");
    }

    private String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private byte[] readBounded(InputStream in, long maxSize) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxSize) {
                throw new IOException("Entry exceeds maximum allowed size: " + maxSize);
            }
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }
}
