package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperiencePrefetchHookTest {

    @Mock
    private ExperienceDisclosureService experienceDisclosureService;

    @Test
    void shouldWritePrefetchStateAndAllowedDirectTools() throws Exception {
        ExperienceDisclosurePayloads.ExperienceCandidateCard directTool = new ExperienceDisclosurePayloads.ExperienceCandidateCard();
        directTool.setCallableToolName("direct_tool");
        directTool.setToolInvocationPath(ExperienceDisclosurePayloads.ToolInvocationPath.REACT_DIRECT);

        ExperienceDisclosurePayloads.ExperienceCandidateCard codeOnlyTool = new ExperienceDisclosurePayloads.ExperienceCandidateCard();
        codeOnlyTool.setCallableToolName("code_only_tool");
        codeOnlyTool.setToolInvocationPath(ExperienceDisclosurePayloads.ToolInvocationPath.CODE_ONLY);

        ExperienceDisclosurePayloads.GroupedExperienceCandidates candidates = new ExperienceDisclosurePayloads.GroupedExperienceCandidates();
        candidates.setToolCandidates(List.of(directTool, codeOnlyTool));

        ExperienceDisclosurePayloads.DirectExperienceGrounding directGrounding =
                new ExperienceDisclosurePayloads.DirectExperienceGrounding();
        directGrounding.setId("common-1");
        directGrounding.setContent("术语解释");

        ExperienceDisclosurePayloads.PrefetchedExperienceSnapshot snapshot =
                new ExperienceDisclosurePayloads.PrefetchedExperienceSnapshot();
        snapshot.setQuery("prefetch query");
        snapshot.setStatus(ExperienceDisclosurePayloads.PrefetchStatus.COMPLETED);
        snapshot.setCandidates(candidates);
        snapshot.setDirectGroundings(List.of(directGrounding));

        ExperienceDisclosureContextResolver resolver = new ExperienceDisclosureContextResolver() {
            @Override
            public String resolveQuery(OverAllState state, RunnableConfig config) {
                return "prefetch query";
            }

            @Override
            public ExperienceQueryContext buildQueryContext(OverAllState state, RunnableConfig config, String userQuery) {
                ExperienceQueryContext context = new ExperienceQueryContext();
                context.setUserQuery(userQuery);
                return context;
            }
        };
        ExperienceExtensionProperties properties = new ExperienceExtensionProperties();
        when(experienceDisclosureService.prefetch(eq("prefetch query"), any(ExperienceQueryContext.class))).thenReturn(snapshot);

        ExperiencePrefetchHook hook = new ExperiencePrefetchHook(experienceDisclosureService, resolver, properties);
        Map<String, Object> updates = hook.beforeAgent(new OverAllState(Map.of("input", "hello")),
                RunnableConfig.builder().threadId("t1").build()).get();

        assertEquals("prefetch query", updates.get(CodeactStateKeys.EXPERIENCE_PREFETCH_QUERY));
        assertEquals("COMPLETED", updates.get(CodeactStateKeys.EXPERIENCE_PREFETCH_STATUS));
        assertEquals(List.of("direct_tool"), updates.get(CodeactStateKeys.EXPERIENCE_ALLOWED_REACT_TOOL_NAMES));
        assertTrue(updates.containsKey(CodeactStateKeys.EXPERIENCE_PREFETCHED_CANDIDATES));
        assertEquals(List.of(directGrounding), updates.get(CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS));
    }
}
