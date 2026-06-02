package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.extension.experience.internal.InMemoryExperienceRepository;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.management.model.SkillImportConflictStrategy;
import com.alibaba.assistant.agent.management.model.SkillPackage;
import com.alibaba.assistant.agent.management.model.SkillPackageImportResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySkillExchangeServiceTest {

    @Test
    void shouldPreviewCliBoundSkillPackageWithPackageTree() {
        InMemorySkillExchangeService service = new InMemorySkillExchangeService(new InMemoryExperienceRepository());

        SkillPackage skillPackage = new SkillPackage();
        skillPackage.setSkillMdContent("""
                ---
                name: A1 Skill
                description: Use A1 for coding tasks
                ---

                # A1 Skill

                Follow the workflow.
                """);
        skillPackage.setName("a1-skill");
        skillPackage.setVersion("0.0.1");
        skillPackage.setScripts(Map.of("scripts/bootstrap.sh", "echo bootstrap"));
        skillPackage.setOtherFiles(Map.of(
                "package.json", """
                        {
                          "name": "a1-skill",
                          "version": "0.0.1"
                        }
                        """.getBytes(),
                "references/guides/setup.md", "guide".getBytes(),
                "assets/templates/config.yaml", "name: demo".getBytes()
        ));
        skillPackage.setPackageMetadata(Map.of(
                "meow", Map.of(
                        "cli", Map.of(
                                "provider", "a1",
                                "toolName", "a1_run",
                                "runnerImage", "registry/a1-cli:1.0.0",
                                "commandAllowPattern", "^a1(\\s|$)",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of("command", Map.of("type", "string"))
                                )
                        )
                )
        ));

        SkillPackageImportResult result = service.previewSkillPackageImport(skillPackage);

        assertNotNull(result.getExperience());
        assertNotNull(result.getToolExperience());
        assertNotNull(result.getReferences());
        assertNotNull(result.getAssets());
        assertEquals("cli", result.getToolExperience().getArtifact().getTool().getSource());
        assertEquals("registry/a1-cli:1.0.0", result.getToolExperience().getArtifact().getTool().getRunnerImage());
        assertTrue(result.getProcessedFiles().contains("scripts/bootstrap.sh"));
        assertTrue(result.getAssets().stream()
                .anyMatch(asset -> "assets/templates/config.yaml".equals(asset.getPath()) && "asset".equals(asset.getRole())));
    }

    @Test
    void shouldImportReactAndToolExperiencesForCliBoundSkill() {
        InMemoryExperienceRepository repository = new InMemoryExperienceRepository();
        InMemorySkillExchangeService service = new InMemorySkillExchangeService(repository);

        SkillPackage skillPackage = new SkillPackage();
        skillPackage.setSkillMdContent("""
                ---
                name: A1 Skill
                description: Use A1 for coding tasks
                ---

                # A1 Skill

                Follow the workflow.
                """);
        skillPackage.setName("a1-skill");
        skillPackage.setVersion("0.0.1");
        skillPackage.setPackageMetadata(Map.of(
                "cli", Map.of(
                        "provider", "a1",
                        "toolName", "a1_run",
                        "commandAllowPattern", "^a1(\\s|$)"
                )
        ));

        SkillPackageImportResult result = service.importSkillPackage(skillPackage);

        assertNotNull(result.getImportedId());
        assertNotNull(result.getImportedToolId());

        List<Experience> reactExperiences = repository.findAllByType(ExperienceType.REACT);
        List<Experience> toolExperiences = repository.findAllByType(ExperienceType.TOOL);
        assertEquals(1, reactExperiences.size());
        assertEquals(1, toolExperiences.size());
        assertEquals(List.of(result.getImportedToolId()), reactExperiences.get(0).getRelatedExperiences());
        assertEquals(List.of(result.getImportedId()), toolExperiences.get(0).getRelatedExperiences());
        assertEquals("cli", toolExperiences.get(0).getArtifact().getTool().getSource());
        assertNotNull(toolExperiences.get(0).getReferences());
        assertNotNull(toolExperiences.get(0).getAssets());
    }

    @Test
    void shouldReportConflictWhenSameNameSkillExistsAndStrategyIsNull() {
        InMemoryExperienceRepository repository = new InMemoryExperienceRepository();
        InMemorySkillExchangeService service = new InMemorySkillExchangeService(repository);

        SkillPackage pkg = newSimpleSkillPackage();
        SkillPackageImportResult first = service.importSkillPackage(pkg);
        assertNotNull(first.getImportedId());

        SkillPackageImportResult second = service.importSkillPackage(newSimpleSkillPackage());
        assertNull(second.getImportedId(), "duplicate import should not save");
        assertTrue(second.hasConflict());
        assertEquals(first.getImportedId(), second.getConflict().getExistingId());
        assertEquals(1, repository.findAllByType(ExperienceType.REACT).size(),
                "no new react experience should be persisted on conflict");
    }

    @Test
    void shouldReplaceExistingReactExperienceWhenStrategyIsReplace() {
        InMemoryExperienceRepository repository = new InMemoryExperienceRepository();
        InMemorySkillExchangeService service = new InMemorySkillExchangeService(repository);

        SkillPackageImportResult first = service.importSkillPackage(newSimpleSkillPackage());
        String originalId = first.getImportedId();
        assertNotNull(originalId);

        SkillPackageImportResult second = service.importSkillPackage(
                newSimpleSkillPackage(), SkillImportConflictStrategy.REPLACE);
        assertEquals(originalId, second.getImportedId(), "replace should reuse existing id");
        assertEquals(1, repository.findAllByType(ExperienceType.REACT).size());
    }

    @Test
    void shouldKeepBothWhenStrategyIsKeepBoth() {
        InMemoryExperienceRepository repository = new InMemoryExperienceRepository();
        InMemorySkillExchangeService service = new InMemorySkillExchangeService(repository);

        SkillPackageImportResult first = service.importSkillPackage(newSimpleSkillPackage());
        String originalId = first.getImportedId();
        assertNotNull(originalId);

        SkillPackageImportResult second = service.importSkillPackage(
                newSimpleSkillPackage(), SkillImportConflictStrategy.KEEP_BOTH);
        assertNotNull(second.getImportedId());
        assertEquals(2, repository.findAllByType(ExperienceType.REACT).size());
    }

    private static SkillPackage newSimpleSkillPackage() {
        SkillPackage pkg = new SkillPackage();
        pkg.setSkillMdContent("""
                ---
                name: Demo Skill
                description: a demo skill
                ---

                # Demo Skill

                body
                """);
        pkg.setName("demo-skill");
        pkg.setVersion("0.0.1");
        return pkg;
    }
}
