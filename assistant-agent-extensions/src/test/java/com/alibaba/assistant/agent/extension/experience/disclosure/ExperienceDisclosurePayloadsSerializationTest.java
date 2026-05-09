package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceDisclosurePayloadsSerializationTest {

    @Test
    void groupedCandidatesShouldBeSerializableForStatePersistence() throws IOException, ClassNotFoundException {
        ExperienceDisclosurePayloads.ExperienceCandidateCard card =
                new ExperienceDisclosurePayloads.ExperienceCandidateCard();
        card.setId("tool-1");
        card.setExperienceType(ExperienceType.TOOL);
        card.setTitle("直调工具");
        card.setDescription("用于验证 state 序列化");
        card.setAssociatedTools(List.of("tool_direct"));
        card.setToolInvocationPath(ExperienceDisclosurePayloads.ToolInvocationPath.REACT_DIRECT);
        card.setCallableToolName("tool_direct");

        ExperienceDisclosurePayloads.GroupedExperienceCandidates candidates =
                new ExperienceDisclosurePayloads.GroupedExperienceCandidates();
        candidates.setToolCandidates(List.of(card));

        ExperienceDisclosurePayloads.GroupedExperienceCandidates restored = roundTrip(candidates);

        assertEquals(1, restored.getToolCandidates().size());
        assertEquals("tool-1", restored.getToolCandidates().get(0).getId());
        assertEquals(ExperienceDisclosurePayloads.ToolInvocationPath.REACT_DIRECT,
                restored.getToolCandidates().get(0).getToolInvocationPath());
    }

    @Test
    void directGroundingShouldBeSerializableForPromptState() throws IOException, ClassNotFoundException {
        ExperienceDisclosurePayloads.DirectExperienceGrounding grounding =
                new ExperienceDisclosurePayloads.DirectExperienceGrounding();
        grounding.setId("common-1");
        grounding.setExperienceType(ExperienceType.COMMON);
        grounding.setTitle("术语解释");
        grounding.setContent("Aone 是产研协同平台。");
        grounding.setDisclosureStrategy("DIRECT");
        grounding.setScore(0.95D);

        ExperienceDisclosurePayloads.DirectExperienceGrounding restored = roundTrip(grounding);

        assertEquals("common-1", restored.getId());
        assertEquals("DIRECT", restored.getDisclosureStrategy());
        assertEquals(0.95D, restored.getScore());
    }

    @Test
    void readResponseShouldBeSerializableWithArtifactForStateCache() throws IOException, ClassNotFoundException {
        ExperienceArtifact.ToolCallSpec toolCallSpec = new ExperienceArtifact.ToolCallSpec();
        toolCallSpec.setToolName("send_message");
        toolCallSpec.setArguments(Map.of("message", "hello"));

        ExperienceArtifact.ToolPlan plan = new ExperienceArtifact.ToolPlan();
        plan.setToolCalls(List.of(toolCallSpec));

        ExperienceArtifact.ReactArtifact reactArtifact = new ExperienceArtifact.ReactArtifact();
        reactArtifact.setAssistantText("done");
        reactArtifact.setPlan(plan);

        ExperienceArtifact.ToolArtifact toolArtifact = new ExperienceArtifact.ToolArtifact();
        toolArtifact.setCodeactToolName("tool_direct");

        ExperienceArtifact artifact = new ExperienceArtifact();
        artifact.setReact(reactArtifact);
        artifact.setTool(toolArtifact);

        ExperienceDisclosurePayloads.ReadExpResponse response = new ExperienceDisclosurePayloads.ReadExpResponse();
        response.setFound(true);
        response.setId("tool-1");
        response.setExperienceType(ExperienceType.TOOL);
        response.setTitle("直调工具");
        response.setArtifact(artifact);
        response.setToolInvocationPath(ExperienceDisclosurePayloads.ToolInvocationPath.REACT_DIRECT);
        response.setCallableToolName("tool_direct");

        ExperienceDisclosurePayloads.ReadExpResponse restored = roundTrip(response);

        assertTrue(restored.isFound());
        assertEquals("tool-1", restored.getId());
        assertNotNull(restored.getArtifact());
        assertEquals("tool_direct", restored.getArtifact().getTool().getCodeactToolName());
        assertEquals("send_message",
                restored.getArtifact().getReact().getPlan().getToolCalls().get(0).getToolName());
    }

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }

        try (ObjectInputStream objectInputStream =
                     new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            return (T) objectInputStream.readObject();
        }
    }
}
