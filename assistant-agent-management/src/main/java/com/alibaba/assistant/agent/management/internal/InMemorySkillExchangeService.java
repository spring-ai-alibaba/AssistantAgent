package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.extension.experience.model.AssetEntry;
import com.alibaba.assistant.agent.extension.experience.model.CliRuntimeConstants;
import com.alibaba.assistant.agent.extension.experience.model.DisclosureStrategy;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.ReferenceEntry;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.management.model.ExperienceVO;
import com.alibaba.assistant.agent.management.model.SkillPackage;
import com.alibaba.assistant.agent.management.model.SkillPackageImportResult;
import com.alibaba.assistant.agent.management.spi.ReferenceSummarizer;
import com.alibaba.assistant.agent.management.spi.SkillExchangeService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class InMemorySkillExchangeService implements SkillExchangeService {

    private final ExperienceRepository repository;
    private final ReferenceSummarizer referenceSummarizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InMemorySkillExchangeService(ExperienceRepository repository) {
        this(repository, new NoopReferenceSummarizer());
    }

    public InMemorySkillExchangeService(ExperienceRepository repository,
                                        ReferenceSummarizer referenceSummarizer) {
        this.repository = repository;
        this.referenceSummarizer = referenceSummarizer != null ? referenceSummarizer : new NoopReferenceSummarizer();
    }

    @Override
    public String importSkill(String skillMarkdown) {
        Experience exp = parseSkillMarkdown(skillMarkdown);
        Experience saved = repository.save(exp);
        return saved.getId();
    }

    @Override
    public String exportSkill(String experienceId) {
        Experience exp = repository.findById(experienceId)
                .orElseThrow(() -> new IllegalArgumentException("Experience not found: " + experienceId));
        return formatSkillMarkdown(exp);
    }

    @Override
    public String exportAllSkills(ExperienceType type) {
        List<Experience> experiences = repository.findAllByType(type);
        return experiences.stream()
                .map(this::formatSkillMarkdown)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    @Override
    public byte[] exportSkillPackage(String experienceId) {
        Experience exp = repository.findById(experienceId)
                .orElseThrow(() -> new IllegalArgumentException("Experience not found: " + experienceId));

        Experience reactExp = exp;
        Experience toolExp = null;
        if (exp.getType() == ExperienceType.TOOL) {
            toolExp = exp;
            reactExp = findCounterpart(exp, ExperienceType.REACT).orElse(exp);
        } else {
            toolExp = findCounterpart(exp, ExperienceType.TOOL).orElse(null);
        }

        String folder = slug(reactExp.getName()) + "/";
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            Set<String> writtenPaths = new LinkedHashSet<>();

            // 1) SKILL.md - rebuild from frontmatter + content (lossless w.r.t. current Experience state)
            putZipEntry(zos, folder + "SKILL.md",
                    formatSkillMarkdown(reactExp).getBytes(StandardCharsets.UTF_8));
            writtenPaths.add("SKILL.md");

            // 2) references (skip the auto-added SKILL.md duplicate; use text content as bytes)
            if (reactExp.getReferences() != null) {
                for (ReferenceEntry r : reactExp.getReferences()) {
                    if (r.getPath() == null || writtenPaths.contains(r.getPath())) {
                        continue;
                    }
                    String content = r.getContent() != null ? r.getContent() : "";
                    putZipEntry(zos, folder + r.getPath(),
                            content.getBytes(StandardCharsets.UTF_8));
                    writtenPaths.add(r.getPath());
                }
            }

            // 3) assets (text → utf8 bytes; non-text → base64 decode)
            if (reactExp.getAssets() != null) {
                for (AssetEntry a : reactExp.getAssets()) {
                    if (a.getPath() == null || writtenPaths.contains(a.getPath())) {
                        continue;
                    }
                    putZipEntry(zos, folder + a.getPath(), decodeAssetBytes(a));
                    writtenPaths.add(a.getPath());
                }
            }

            // 4) package.json - reconstruct from CLI TOOL artifact if present
            if (toolExp != null && toolExp.getArtifact() != null
                    && toolExp.getArtifact().getTool() != null
                    && !writtenPaths.contains("package.json")) {
                byte[] pkgJson = buildPackageJsonBytes(reactExp, toolExp);
                if (pkgJson != null) {
                    putZipEntry(zos, folder + "package.json", pkgJson);
                }
            }

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build skill package zip", e);
        }
    }

    private Optional<Experience> findCounterpart(Experience exp, ExperienceType wantedType) {
        List<String> related = exp.getRelatedExperiences();
        if (related == null) {
            return Optional.empty();
        }
        for (String relatedId : related) {
            Optional<Experience> opt = repository.findById(relatedId);
            if (opt.isPresent() && opt.get().getType() == wantedType) {
                return opt;
            }
        }
        return Optional.empty();
    }

    private static void putZipEntry(ZipOutputStream zos, String path, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        if (data != null && data.length > 0) {
            zos.write(data);
        }
        zos.closeEntry();
    }

    private byte[] decodeAssetBytes(AssetEntry a) {
        String content = a.getContent();
        if (content == null || content.isEmpty()) {
            return new byte[0];
        }
        if (isTextFile(a.getPath())) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException ex) {
            // Defensive: fall back to UTF-8 bytes when content was unexpectedly plain text.
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] buildPackageJsonBytes(Experience reactExp, Experience toolExp) {
        ExperienceArtifact.ToolArtifact tool = toolExp.getArtifact().getTool();
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("name", reactExp.getName() != null ? reactExp.getName() : toolExp.getName());
        if (reactExp.getMetadata() != null && reactExp.getMetadata().getVersion() != null) {
            pkg.put("version", reactExp.getMetadata().getVersion());
        }
        if (reactExp.getDescription() != null && !reactExp.getDescription().isBlank()) {
            pkg.put("description", reactExp.getDescription());
        }

        Map<String, Object> cli = new LinkedHashMap<>();
        putIfNotBlank(cli, "provider", tool.getProviderId());
        putIfNotBlank(cli, "runnerImage", tool.getRunnerImage());
        putIfNotBlank(cli, "sandboxTemplate", tool.getSandboxTemplate());
        putIfNotBlank(cli, "executionMode", tool.getExecutionMode());
        putIfNotBlank(cli, "authProfile", tool.getAuthProfile());
        putIfNotBlank(cli, "authProvider", tool.getAuthProvider());
        putIfNotBlank(cli, "loginCommandTemplate", tool.getLoginCommandTemplate());
        putIfNotBlank(cli, "commandAllowPattern", tool.getCommandAllowPattern());
        if (tool.getPipeAllowlist() != null && !tool.getPipeAllowlist().isEmpty()) {
            cli.put("pipeAllowlist", tool.getPipeAllowlist());
        }
        putIfNotBlank(cli, "outputFormat", tool.getOutputFormat());
        putIfNotBlank(cli, "returnDescription", tool.getReturnDescription());
        if (tool.isReturnDirect()) {
            cli.put("returnDirect", true);
        }
        if (toolExp.getDescription() != null && !toolExp.getDescription().isBlank()) {
            cli.put("description", toolExp.getDescription());
        }
        if (cli.isEmpty()) {
            return null;
        }
        pkg.put("cli", cli);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    @Override
    public ExperienceVO previewSkillImport(String skillMarkdown) {
        Experience exp = parseSkillMarkdown(skillMarkdown);
        return ExperienceVO.fromExperience(exp);
    }

    @Override
    public SkillPackageImportResult importSkillPackage(SkillPackage skillPackage) {
        PreparedSkillImport prepared = prepareSkillPackageImport(skillPackage);
        if (prepared.reactExperience == null) {
            return prepared.result;
        }

        Experience savedReact = repository.save(prepared.reactExperience);
        prepared.result.setImportedId(savedReact.getId());
        prepared.result.setExperience(ExperienceVO.fromExperience(savedReact));

        if (prepared.toolExperience != null) {
            Experience savedTool = repository.save(prepared.toolExperience);
            prepared.result.setImportedToolId(savedTool.getId());
            prepared.result.setToolExperience(ExperienceVO.fromExperience(savedTool));
        }

        return prepared.result;
    }

    @Override
    public SkillPackageImportResult previewSkillPackageImport(SkillPackage skillPackage) {
        PreparedSkillImport prepared = prepareSkillPackageImport(skillPackage);
        if (prepared.reactExperience == null) {
            return prepared.result;
        }

        prepared.result.setExperience(ExperienceVO.fromExperience(prepared.reactExperience));
        if (prepared.toolExperience != null) {
            prepared.result.setToolExperience(ExperienceVO.fromExperience(prepared.toolExperience));
        }
        return prepared.result;
    }

    private PreparedSkillImport prepareSkillPackageImport(SkillPackage skillPackage) {
        SkillPackageImportResult result = new SkillPackageImportResult();
        if (!skillPackage.hasSkillMd()) {
            result.addWarning("No SKILL.md found in package");
            return new PreparedSkillImport(result, null, null);
        }

        Experience existingReact = findExistingReactExperienceByPackage(skillPackage).orElse(null);
        Map<String, String> existingDescriptionsByHash = buildDescriptionCache(existingReact);

        Experience reactExperience = parseSkillMarkdown(skillPackage.getSkillMdContent());
        enrichExperienceFromPackage(reactExperience, skillPackage);

        List<ReferenceEntry> references = new ArrayList<>();
        List<AssetEntry> assets = new ArrayList<>();
        buildReferencesAndAssets(skillPackage, references, assets, existingDescriptionsByHash);
        reactExperience.setReferences(references);
        reactExperience.setAssets(assets);

        result.setReferences(references);
        result.setAssets(assets.stream().map(InMemorySkillExchangeService::assetWithoutContent).toList());
        collectProcessedFiles(skillPackage, result);

        Experience toolExperience = null;
        CliBinding cliBinding = extractCliBinding(skillPackage.getPackageMetadata());
        if (cliBinding != null) {
            toolExperience = buildCliToolExperience(skillPackage, reactExperience, cliBinding);
            String cliToolName = CliRuntimeConstants.toolName(cliBinding.provider());
            reactExperience.setAssociatedTools(new ArrayList<>(List.of(cliToolName)));
            List<String> related = new ArrayList<>();
            if (toolExperience.getId() != null) {
                related.add(toolExperience.getId());
            }
            reactExperience.setRelatedExperiences(related);
        }

        return new PreparedSkillImport(result, reactExperience, toolExperience);
    }

    private Optional<Experience> findExistingReactExperienceByPackage(SkillPackage skillPackage) {
        if (skillPackage.getName() == null || skillPackage.getName().isBlank()) {
            return Optional.empty();
        }
        return repository.findAllByType(ExperienceType.REACT).stream()
                .filter(e -> skillPackage.getName().equalsIgnoreCase(e.getName()))
                .findFirst();
    }

    private Map<String, String> buildDescriptionCache(Experience existing) {
        Map<String, String> cache = new HashMap<>();
        if (existing == null) {
            return cache;
        }
        if (existing.getReferences() != null) {
            for (ReferenceEntry ref : existing.getReferences()) {
                if (ref.getContentHash() != null && ref.getDescription() != null) {
                    cache.put(ref.getContentHash(), ref.getDescription());
                }
            }
        }
        if (existing.getAssets() != null) {
            for (AssetEntry asset : existing.getAssets()) {
                if (asset.getContentHash() != null && asset.getDescription() != null) {
                    cache.put(asset.getContentHash(), asset.getDescription());
                }
            }
        }
        return cache;
    }

    private void collectProcessedFiles(SkillPackage skillPackage, SkillPackageImportResult result) {
        result.addProcessedFile("SKILL.md");
        if (skillPackage.hasScripts()) {
            for (String path : skillPackage.getScripts().keySet()) {
                result.addProcessedFile(path);
            }
        }
        if (skillPackage.hasOtherFiles()) {
            for (String path : skillPackage.getOtherFiles().keySet()) {
                result.addProcessedFile(path);
            }
        }
    }

    private void enrichExperienceFromPackage(Experience exp, SkillPackage skillPackage) {
        if ((exp.getName() == null || exp.getName().isBlank()) && skillPackage.getName() != null) {
            exp.setName(skillPackage.getName());
        }
        if ((exp.getDescription() == null || exp.getDescription().isBlank()) && skillPackage.getDescription() != null) {
            exp.setDescription(skillPackage.getDescription());
        }
        if (skillPackage.getVersion() != null) {
            exp.getMetadata().setVersion(skillPackage.getVersion());
        }
    }

    /**
     * 遍历 skill 包中的脚本/附件，根据路径规则分类为 references 或 assets，
     * 并通过 {@link #batchResolveDescriptions} 并行生成缺失的 description。
     */
    private void buildReferencesAndAssets(SkillPackage skillPackage,
                                          List<ReferenceEntry> references,
                                          List<AssetEntry> assets,
                                          Map<String, String> descriptionCache) {
        // SKILL.md 同时保留为 reference（role=skill-md），以便 read_exp_doc 可按 path 检索。
        if (skillPackage.hasSkillMd()) {
            String content = skillPackage.getSkillMdContent();
            ReferenceEntry skillRef = new ReferenceEntry();
            skillRef.setPath("SKILL.md");
            skillRef.setMediaType("text/markdown");
            skillRef.setContent(content);
            skillRef.setContentHash(DescriptionResolver.sha256Hex(content));
            skillRef.setSize((long) content.getBytes(StandardCharsets.UTF_8).length);
            references.add(skillRef);
        }

        List<PendingEntry> pending = new ArrayList<>();

        if (skillPackage.hasScripts()) {
            skillPackage.getScripts().forEach((path, content) -> pending.add(
                    new PendingEntry(path, content.getBytes(StandardCharsets.UTF_8), true, content)));
        }
        if (skillPackage.hasOtherFiles()) {
            skillPackage.getOtherFiles().forEach((path, bytes) -> {
                boolean text = isTextFile(path);
                String textContent = text && bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
                pending.add(new PendingEntry(path, bytes != null ? bytes : new byte[0], text, textContent));
            });
        }

        batchResolveDescriptions(pending, descriptionCache);

        for (PendingEntry pe : pending) {
            SkillContentClassifier.Bucket bucket = SkillContentClassifier.classify(pe.path);
            if (bucket == SkillContentClassifier.Bucket.REFERENCE) {
                ReferenceEntry ref = new ReferenceEntry();
                ref.setPath(pe.path);
                ref.setMediaType(detectMediaType(pe.path));
                ref.setContent(pe.textContent != null
                        ? pe.textContent
                        : new String(pe.bytes, StandardCharsets.UTF_8));
                ref.setContentHash(pe.contentHash);
                ref.setSize((long) pe.bytes.length);
                ref.setDescription(pe.description);
                references.add(ref);
            } else {
                AssetEntry asset = new AssetEntry();
                asset.setPath(pe.path);
                asset.setMediaType(detectMediaType(pe.path));
                asset.setRole(SkillContentClassifier.assetRoleFor(pe.path));
                asset.setContentHash(pe.contentHash);
                asset.setSize((long) pe.bytes.length);
                asset.setDescription(pe.description);
                if (pe.isText && pe.textContent != null) {
                    asset.setContent(pe.textContent);
                } else {
                    asset.setContent(pe.bytes.length > 0 ? Base64.getEncoder().encodeToString(pe.bytes) : "");
                }
                assets.add(asset);
            }
        }

        references.sort(Comparator.comparing(ReferenceEntry::getPath));
        assets.sort(Comparator.comparing(AssetEntry::getPath));
    }

    /**
     * 并行（最多 4 个线程）为每个 pending entry 生成 description；命中 {@code descriptionCache}
     * 时跳过 summarizer 调用。
     */
    private void batchResolveDescriptions(List<PendingEntry> pending, Map<String, String> cache) {
        if (pending.isEmpty()) {
            return;
        }
        for (PendingEntry pe : pending) {
            pe.contentHash = DescriptionResolver.sha256Hex(pe.bytes);
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, pending.size()));
        long start = System.currentTimeMillis();
        int ok = 0;
        int fail = 0;
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (PendingEntry pe : pending) {
                String cached = pe.contentHash != null ? cache.get(pe.contentHash) : null;
                if (cached != null) {
                    pe.description = cached;
                    continue;
                }
                futures.add(executor.submit(() -> {
                    pe.description = DescriptionResolver.resolve(pe.path, pe.textContent, referenceSummarizer);
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                    ok++;
                } catch (Exception e) {
                    fail++;
                }
            }
        } finally {
            executor.shutdown();
        }
        long elapsed = System.currentTimeMillis() - start;
        // 控制台可见的单行概要；正式日志由上层记录。
        System.out.println("[skill-import] summarize cost=" + elapsed + "ms ok=" + ok + " fail=" + fail
                + " total=" + pending.size());
    }

    private static AssetEntry assetWithoutContent(AssetEntry src) {
        AssetEntry copy = new AssetEntry();
        copy.setPath(src.getPath());
        copy.setMediaType(src.getMediaType());
        copy.setRole(src.getRole());
        copy.setDescription(src.getDescription());
        copy.setSize(src.getSize());
        copy.setContentHash(src.getContentHash());
        copy.setContentRef(src.getContentRef());
        return copy;
    }

    private Experience buildCliToolExperience(SkillPackage skillPackage,
                                              Experience reactExperience,
                                              CliBinding cliBinding) {
        String toolExperienceId = CliRuntimeConstants.toolExperienceId(cliBinding.provider());
        String toolName = CliRuntimeConstants.toolName(cliBinding.provider());

        Experience existing = repository.findById(toolExperienceId).orElse(null);
        Experience toolExperience = existing != null ? existing : new Experience();
        toolExperience.setId(toolExperienceId);
        toolExperience.setType(ExperienceType.TOOL);
        toolExperience.setName(toolName);
        toolExperience.setDescription(firstNonBlank(cliBinding.description(),
                "CLI tool for provider: " + cliBinding.provider()));
        toolExperience.setContent(buildCliToolContent(cliBinding));
        toolExperience.setDisclosureStrategy(DisclosureStrategy.PROGRESSIVE);
        toolExperience.setAssociatedTools(new ArrayList<>(List.of(toolName)));

        List<String> relatedExperiences = new ArrayList<>();
        if (existing != null && existing.getRelatedExperiences() != null) {
            relatedExperiences.addAll(existing.getRelatedExperiences());
        }
        if (reactExperience.getId() != null && !relatedExperiences.contains(reactExperience.getId())) {
            relatedExperiences.add(reactExperience.getId());
        }
        toolExperience.setRelatedExperiences(relatedExperiences);

        toolExperience.getMetadata().setSource("cli:" + cliBinding.provider());
        toolExperience.getMetadata().putProperty("cliProvider", cliBinding.provider());
        toolExperience.getMetadata().putProperty("cliExecutionMode", cliBinding.executionMode());
        toolExperience.getMetadata().putProperty("cliAuthProfile", cliBinding.authProfile());
        toolExperience.getMetadata().setVersion(skillPackage.getVersion());

        Set<String> tags = new HashSet<>();
        if (toolExperience.getTags() != null) {
            tags.addAll(toolExperience.getTags());
        }
        tags.add("source:cli");
        tags.add("provider:" + cliBinding.provider());
        toolExperience.setTags(tags);

        ExperienceArtifact artifact = new ExperienceArtifact();

        ExperienceArtifact.ToolArtifact toolArtifact = new ExperienceArtifact.ToolArtifact();
        toolArtifact.setSource(CliRuntimeConstants.SOURCE_CLI);
        toolArtifact.setProviderId(cliBinding.provider());
        toolArtifact.setRunnerImage(cliBinding.runnerImage());
        toolArtifact.setSandboxTemplate(cliBinding.sandboxTemplate());
        toolArtifact.setExecutionMode(cliBinding.executionMode());
        toolArtifact.setAuthProfile(cliBinding.authProfile());
        toolArtifact.setAuthProvider(cliBinding.authProvider());
        toolArtifact.setLoginCommandTemplate(cliBinding.loginCommandTemplate());
        toolArtifact.setCommandAllowPattern(cliBinding.commandAllowPattern());
        toolArtifact.setPipeAllowlist(cliBinding.pipeAllowlist());
        toolArtifact.setOutputFormat(cliBinding.outputFormat());
        toolArtifact.setInputSchema(buildCommandInputSchema(cliBinding));
        toolArtifact.setReturnDescription(cliBinding.returnDescription());
        toolArtifact.setReturnDirect(cliBinding.returnDirect());
        toolArtifact.setCodeactToolName(toolName);
        artifact.setTool(toolArtifact);

        toolExperience.setArtifact(artifact);
        // CLI 执行器拿到的是 TOOL 经验，需要把 reference/asset 一并挂上，以便 materializer 能将
        // 文件写入沙箱 workspace。这里做深拷贝避免后续持久化时两个经验互相引用同一对象。
        toolExperience.setReferences(copyReferences(reactExperience.getReferences()));
        toolExperience.setAssets(copyAssets(reactExperience.getAssets()));
        return toolExperience;
    }

    private static List<ReferenceEntry> copyReferences(List<ReferenceEntry> src) {
        if (src == null) {
            return new ArrayList<>();
        }
        List<ReferenceEntry> out = new ArrayList<>(src.size());
        for (ReferenceEntry r : src) {
            ReferenceEntry c = new ReferenceEntry();
            c.setPath(r.getPath());
            c.setMediaType(r.getMediaType());
            c.setDescription(r.getDescription());
            c.setContent(r.getContent());
            c.setContentHash(r.getContentHash());
            c.setSize(r.getSize());
            out.add(c);
        }
        return out;
    }

    private static List<AssetEntry> copyAssets(List<AssetEntry> src) {
        if (src == null) {
            return new ArrayList<>();
        }
        List<AssetEntry> out = new ArrayList<>(src.size());
        for (AssetEntry a : src) {
            AssetEntry c = new AssetEntry();
            c.setPath(a.getPath());
            c.setMediaType(a.getMediaType());
            c.setRole(a.getRole());
            c.setDescription(a.getDescription());
            c.setSize(a.getSize());
            c.setContent(a.getContent());
            c.setContentRef(a.getContentRef());
            c.setContentHash(a.getContentHash());
            out.add(c);
        }
        return out;
    }

    private String buildCommandInputSchema(CliBinding cliBinding) {
        Map<String, Object> commandSchema = new LinkedHashMap<>();
        commandSchema.put("type", "string");
        String allow = cliBinding.commandAllowPattern();
        StringBuilder desc = new StringBuilder("Bash command to run inside the ")
                .append(cliBinding.provider())
                .append(" CLI sandbox.");
        if (allow != null && !allow.isBlank()) {
            desc.append(" Command MUST match regex: ").append(allow).append('.');
        }
        if (cliBinding.pipeAllowlist() != null && !cliBinding.pipeAllowlist().isEmpty()) {
            desc.append(" Allowed pipe utilities: ")
                    .append(String.join(",", cliBinding.pipeAllowlist())).append('.');
        }
        commandSchema.put("description", desc.toString());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(CliRuntimeConstants.COMMAND_PARAM, commandSchema);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(CliRuntimeConstants.COMMAND_PARAM));
        return toJson(schema);
    }

    private String buildCliToolContent(CliBinding cliBinding) {
        StringBuilder sb = new StringBuilder();
        sb.append("## CLI Tool (").append(cliBinding.provider()).append(")\n\n");
        sb.append("**Provider**: ").append(cliBinding.provider()).append("\n\n");
        sb.append("**Execution**: ").append(cliBinding.executionMode()).append("\n\n");
        sb.append("**Auth**: ").append(cliBinding.authProfile()).append("\n\n");
        if (cliBinding.commandAllowPattern() != null && !cliBinding.commandAllowPattern().isBlank()) {
            sb.append("**Allowed command pattern**: `")
                    .append(cliBinding.commandAllowPattern()).append("`\n\n");
        }
        if (cliBinding.description() != null && !cliBinding.description().isBlank()) {
            sb.append(cliBinding.description()).append("\n\n");
        }
        sb.append("This tool accepts a freeform `command` argument. Supply the exact bash command you want to run; ");
        sb.append("the server will validate it against the allow-pattern, materialise the skill package inside a ");
        sb.append("reusable sandbox, perform the login flow on first invocation, and execute the command.\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private CliBinding extractCliBinding(Map<String, Object> packageMetadata) {
        if (packageMetadata == null || packageMetadata.isEmpty()) {
            return null;
        }

        Object cliSection = packageMetadata.get("cli");
        if (!(cliSection instanceof Map<?, ?>)) {
            for (String namespace : List.of("meow", "meowAgent", "x-meow-agent", "xMeowAgent", "a1")) {
                Object namespaceValue = packageMetadata.get(namespace);
                if (namespaceValue instanceof Map<?, ?> namespaceMap && namespaceMap.get("cli") instanceof Map<?, ?> nestedCli) {
                    cliSection = nestedCli;
                    break;
                }
            }
        }

        if (!(cliSection instanceof Map<?, ?> rawCliMap)) {
            return null;
        }

        Map<String, Object> cliMap = new LinkedHashMap<>();
        rawCliMap.forEach((key, value) -> cliMap.put(String.valueOf(key), value));

        String provider = asText(cliMap.get("provider"));
        if (provider == null || provider.isBlank()) {
            return null;
        }

        List<String> pipeAllowlist = extractPipeAllowlist(cliMap.get("pipeAllowlist"));
        return new CliBinding(
                provider,
                asText(cliMap.get("description")),
                firstNonBlank(asText(cliMap.get("runnerImage")), provider + "-cli:latest"),
                firstNonBlank(asText(cliMap.get("sandboxTemplate")), CliRuntimeConstants.SANDBOX_TEMPLATE_CODE_INTERPRETER),
                firstNonBlank(asText(cliMap.get("executionMode")), CliRuntimeConstants.EXECUTION_MODE_SANDBOX),
                firstNonBlank(asText(cliMap.get("authProfile")), CliRuntimeConstants.AUTH_PROFILE_USER_TOKEN_BROKER),
                firstNonBlank(asText(cliMap.get("authProvider")), provider),
                firstNonBlank(asText(cliMap.get("loginCommandTemplate")), defaultLoginTemplate(provider)),
                asText(cliMap.get("commandAllowPattern")),
                pipeAllowlist,
                firstNonBlank(asText(cliMap.get("outputFormat")), CliRuntimeConstants.OUTPUT_FORMAT_TEXT),
                asText(cliMap.get("returnDescription")),
                Boolean.parseBoolean(String.valueOf(cliMap.getOrDefault("returnDirect", false)))
        );
    }

    private List<String> extractPipeAllowlist(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String s = String.valueOf(item).trim();
                    if (!s.isBlank()) {
                        result.add(s);
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    private String defaultLoginTemplate(String provider) {
        if ("a1".equalsIgnoreCase(provider)) {
            return "echo \"{{token}}\" | a1 auth login --platform code --with-token";
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String asText(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private boolean isTextFile(String path) {
        String lowerPath = path.toLowerCase();
        return lowerPath.endsWith(".md")
                || lowerPath.endsWith(".txt")
                || lowerPath.endsWith(".json")
                || lowerPath.endsWith(".yaml")
                || lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".sh")
                || lowerPath.endsWith(".py")
                || lowerPath.endsWith(".xml")
                || lowerPath.endsWith(".properties")
                || lowerPath.endsWith(".js")
                || lowerPath.endsWith(".ts")
                || lowerPath.endsWith(".sql");
    }

    private String detectMediaType(String path) {
        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".md")) {
            return "text/markdown";
        }
        if (lowerPath.endsWith(".json")) {
            return "application/json";
        }
        if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
            return "application/yaml";
        }
        if (lowerPath.endsWith(".sh")) {
            return "application/x-sh";
        }
        if (lowerPath.endsWith(".py")) {
            return "text/x-python";
        }
        if (lowerPath.endsWith(".txt")) {
            return "text/plain";
        }
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        }
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return isTextFile(path) ? "text/plain" : "application/octet-stream";
    }

    private String slug(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    /**
     * 导入过程中暂存的文件条目：同时持有字节、文本视图、哈希与（异步）描述。
     */
    private static final class PendingEntry {
        final String path;
        final byte[] bytes;
        final boolean isText;
        final String textContent;
        volatile String contentHash;
        volatile String description;

        PendingEntry(String path, byte[] bytes, boolean isText, String textContent) {
            this.path = path;
            this.bytes = bytes;
            this.isText = isText;
            this.textContent = textContent;
        }
    }

    private String formatSkillMarkdown(Experience exp) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(nullSafe(exp.getName())).append("\n");
        sb.append("type: ").append(exp.getType() != null ? exp.getType().name() : "").append("\n");
        sb.append("description: ").append(nullSafe(exp.getDescription())).append("\n");
        sb.append("tags: [").append(formatTags(exp.getTags())).append("]\n");
        sb.append("---\n\n");
        sb.append("# ").append(nullSafe(exp.getName())).append("\n\n");
        sb.append(nullSafe(exp.getContent()));
        return sb.toString();
    }

    private Experience parseSkillMarkdown(String skillMarkdown) {
        Experience exp = new Experience();

        String frontmatter = "";
        String body = skillMarkdown;

        if (skillMarkdown.startsWith("---")) {
            int endIndex = skillMarkdown.indexOf("---", 3);
            if (endIndex > 0) {
                frontmatter = skillMarkdown.substring(3, endIndex).trim();
                body = skillMarkdown.substring(endIndex + 3).trim();
            }
        }

        for (String line : frontmatter.split("\n")) {
            line = line.trim();
            int colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            String key = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();

            switch (key) {
                case "name" -> exp.setName(value);
                case "type" -> {
                    try {
                        exp.setType(ExperienceType.valueOf(value));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                case "description" -> exp.setDescription(value);
                case "tags" -> exp.setTags(parseTags(value));
                case "version" -> exp.getMetadata().setVersion(value);
                default -> {
                }
            }
        }

        if (exp.getType() == null) {
            exp.setType(ExperienceType.REACT);
        }

        if (exp.getDisclosureStrategy() == null && exp.getType() == ExperienceType.REACT) {
            exp.setDisclosureStrategy(DisclosureStrategy.PROGRESSIVE);
        }

        exp.getMetadata().setSource("skill-import");

        if (body.startsWith("# ")) {
            int newlineIdx = body.indexOf('\n');
            if (newlineIdx > 0) {
                body = body.substring(newlineIdx + 1).trim();
            }
        }
        exp.setContent(body);

        return exp;
    }

    private Set<String> parseTags(String value) {
        Set<String> tags = new LinkedHashSet<>();
        value = value.trim();
        if (value.startsWith("[")) {
            value = value.substring(1);
        }
        if (value.endsWith("]")) {
            value = value.substring(0, value.length() - 1);
        }
        for (String tag : value.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    private String formatTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (String tag : tags) {
            joiner.add(tag);
        }
        return joiner.toString();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private record PreparedSkillImport(SkillPackageImportResult result,
                                       Experience reactExperience,
                                       Experience toolExperience) {
    }

    private record CliBinding(String provider,
                              String description,
                              String runnerImage,
                              String sandboxTemplate,
                              String executionMode,
                              String authProfile,
                              String authProvider,
                              String loginCommandTemplate,
                              String commandAllowPattern,
                              List<String> pipeAllowlist,
                              String outputFormat,
                              String returnDescription,
                              boolean returnDirect) {
    }
}
