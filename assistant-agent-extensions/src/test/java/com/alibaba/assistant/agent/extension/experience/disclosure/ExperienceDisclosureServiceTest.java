package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.model.DisclosureStrategy;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperienceDisclosureServiceTest {

    @Mock
    private ExperienceProvider experienceProvider;

    @Mock
    private ExperienceRepository experienceRepository;

    private ExperienceDisclosureService service;

    @BeforeEach
    void setUp() {
        ExperienceExtensionProperties properties = new ExperienceExtensionProperties();
        properties.setMaxItemsPerQuery(4);
        properties.setReactDirectToolNames(List.of("tool_direct"));
        service = new ExperienceDisclosureService(
                experienceProvider,
                experienceRepository,
                properties,
                new ExperienceToolInvocationClassifier(properties)
        );
    }

    @Test
    void shouldGroupCommonReactAndToolCandidatesSeparately() {
        Experience common = experience("common-1", ExperienceType.COMMON, "Aone", "平台术语解释", null);
        Experience react = experience("react-1", ExperienceType.REACT, "处理发布问题", "发布流程经验", null);
        Experience tool = experience("tool-1", ExperienceType.TOOL, "直调工具", "可直接调用的能力", "tool_direct");

        when(experienceProvider.query(argThat(queryWithType(ExperienceType.COMMON)), argThat(contextWith("tenant-a", "query"))))
                .thenReturn(List.of(common));
        when(experienceProvider.query(argThat(queryWithType(ExperienceType.REACT)), argThat(contextWith("tenant-a", "query"))))
                .thenReturn(List.of(react));
        when(experienceProvider.query(argThat(queryWithType(ExperienceType.TOOL)), argThat(contextWith("tenant-a", "query"))))
                .thenReturn(List.of(tool));

        ExperienceDisclosurePayloads.SearchExpResponse response = service.search(
                "query", null, null, null, context("tenant-a", "query"));

        assertEquals(1, response.getCandidates().getCommonCandidates().size());
        assertEquals("common-1", response.getCandidates().getCommonCandidates().get(0).getId());
        assertEquals(0.92D, response.getCandidates().getCommonCandidates().get(0).getScore());
        assertEquals(1, response.getCandidates().getReactCandidates().size());
        assertEquals("react-1", response.getCandidates().getReactCandidates().get(0).getId());
        assertEquals(1, response.getCandidates().getToolCandidates().size());
        assertEquals(ExperienceDisclosurePayloads.ToolInvocationPath.REACT_DIRECT,
                response.getCandidates().getToolCandidates().get(0).getToolInvocationPath());
        assertEquals("tool_direct",
                response.getCandidates().getToolCandidates().get(0).getCallableToolName());
        assertEquals(1, response.getDirectGroundings().size());
        assertEquals("common-1", response.getDirectGroundings().get(0).getId());
        assertEquals("平台术语解释", response.getDirectGroundings().get(0).getContent());
    }

    @Test
    void shouldReadFullDetailWithToolInvocationMetadata() {
        Experience tool = experience("tool-1", ExperienceType.TOOL, "直调工具", "完整内容", "tool_direct");
        tool.setDescription("工具说明");

        when(experienceRepository.findById("tool-1")).thenReturn(Optional.of(tool));

        ExperienceDisclosurePayloads.ReadExpResponse response = service.read("tool-1");

        assertTrue(response.isFound());
        assertEquals("tool-1", response.getId());
        assertEquals("直调工具", response.getTitle());
        assertEquals("工具说明", response.getDescription());
        assertEquals("完整内容", response.getContent());
        assertEquals(0.92D, response.getScore());
        assertEquals(ExperienceDisclosurePayloads.ToolInvocationPath.REACT_DIRECT, response.getToolInvocationPath());
        assertEquals("tool_direct", response.getCallableToolName());
        assertNotNull(response.getArtifact());
    }

    @Test
    void shouldSkipDirectGroundingForLongOrLowConfidenceContent() {
        Experience lowConfidence = experience("common-2", ExperienceType.COMMON, "低置信概念", "短内容", null);
        lowConfidence.getMetadata().setConfidence(0.5D);

        Experience longContent = experience("common-3", ExperienceType.COMMON, "长内容概念", "x".repeat(501), null);
        longContent.getMetadata().setConfidence(0.95D);

        when(experienceProvider.query(argThat(queryWithType(ExperienceType.COMMON)), argThat(contextWith("tenant-a", "query"))))
                .thenReturn(List.of(lowConfidence, longContent));
        when(experienceProvider.query(argThat(queryWithType(ExperienceType.REACT)), argThat(contextWith("tenant-a", "query"))))
                .thenReturn(List.of());
        when(experienceProvider.query(argThat(queryWithType(ExperienceType.TOOL)), argThat(contextWith("tenant-a", "query"))))
                .thenReturn(List.of());

        ExperienceDisclosurePayloads.SearchExpResponse response = service.search(
                "query", null, null, null, context("tenant-a", "query"));

        assertTrue(response.getDirectGroundings().isEmpty());
    }

    @Test
    void shouldUseExplicitToolInvocationPathWhenConfiguredInMetadata() {
        Experience tool = experience("tool-2", ExperienceType.TOOL, "后台直调工具", "完整内容", "tool_indirect");
        tool.getMetadata().putProperty("toolInvocationPath", "REACT_DIRECT");

        when(experienceRepository.findById("tool-2")).thenReturn(Optional.of(tool));

        ExperienceDisclosurePayloads.ReadExpResponse response = service.read("tool-2");

        assertEquals(ExperienceDisclosurePayloads.ToolInvocationPath.REACT_DIRECT, response.getToolInvocationPath());
    }

    @Test
    void shouldLetExplicitToolInvocationPathOverrideConfiguredAllowlist() {
        Experience tool = experience("tool-3", ExperienceType.TOOL, "后台改为代码工具", "完整内容", "tool_direct");
        tool.getMetadata().putProperty("toolInvocationPath", "CODE_ONLY");

        when(experienceRepository.findById("tool-3")).thenReturn(Optional.of(tool));

        ExperienceDisclosurePayloads.ReadExpResponse response = service.read("tool-3");

        assertEquals(ExperienceDisclosurePayloads.ToolInvocationPath.CODE_ONLY, response.getToolInvocationPath());
    }

    private ExperienceQueryContext context(String tenantId, String userQuery) {
        ExperienceQueryContext context = new ExperienceQueryContext();
        context.setTenantId(tenantId);
        context.setUserQuery(userQuery);
        return context;
    }

    private ArgumentMatcher<ExperienceQueryContext> contextWith(String tenantId, String userQuery) {
        return context -> context != null
                && tenantId.equals(context.getTenantId())
                && userQuery.equals(context.getUserQuery());
    }

    private ArgumentMatcher<ExperienceQuery> queryWithType(ExperienceType type) {
        return query -> query != null && query.getType() == type;
    }

    private Experience experience(String id, ExperienceType type, String name, String content, String toolName) {
        Experience experience = new Experience();
        experience.setId(id);
        experience.setType(type);
        experience.setName(name);
        experience.setDescription(name + "描述");
        experience.setContent(content);
        experience.setDisclosureStrategy(type == ExperienceType.COMMON ? DisclosureStrategy.DIRECT : DisclosureStrategy.PROGRESSIVE);
        experience.getMetadata().setConfidence(0.92D);
        if (toolName != null) {
            ExperienceArtifact artifact = new ExperienceArtifact();
            ExperienceArtifact.ToolArtifact toolArtifact = new ExperienceArtifact.ToolArtifact();
            toolArtifact.setCodeactToolName(toolName);
            artifact.setTool(toolArtifact);
            experience.setArtifact(artifact);
            experience.setAssociatedTools(List.of(toolName));
        }
        return experience;
    }
}
