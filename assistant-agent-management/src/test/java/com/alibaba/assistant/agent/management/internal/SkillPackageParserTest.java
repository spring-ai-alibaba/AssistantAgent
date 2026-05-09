package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.management.model.SkillPackage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPackageParserTest {

    @Test
    void shouldParseStructuredPackageMetadataFromPackageJson() throws Exception {
        SkillPackageParser parser = new SkillPackageParser();

        byte[] zipData = buildZip(Map.of(
                "demo-skill/SKILL.md", """
                        ---
                        name: demo
                        description: demo skill
                        ---

                        # Demo

                        hello
                        """,
                "demo-skill/package.json", """
                        {
                          "name": "demo-from-package",
                          "version": "1.0.1",
                          "description": "cli skill",
                          "meow": {
                            "cli": {
                              "provider": "a1",
                              "toolName": "a1_demo",
                              "commandAllowPattern": "^a1(\\\\s|$)"
                            }
                          }
                        }
                        """
        ));

        SkillPackage skillPackage = parser.parseZip(new ByteArrayInputStream(zipData));

        assertEquals("demo-from-package", skillPackage.getName());
        assertEquals("1.0.1", skillPackage.getVersion());
        assertEquals("cli skill", skillPackage.getDescription());
        assertTrue(skillPackage.getPackageMetadata().containsKey("meow"));
        Object meow = skillPackage.getPackageMetadata().get("meow");
        assertInstanceOf(Map.class, meow);
        Object cli = ((Map<?, ?>) meow).get("cli");
        assertInstanceOf(Map.class, cli);
        assertEquals("a1", ((Map<?, ?>) cli).get("provider"));
    }

    private byte[] buildZip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}
