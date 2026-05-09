package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.DirectExperienceGrounding;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceRuntimeToolStateInterceptorTest {

    @Test
    void shouldMergeDirectGroundingsFromSearchResponseIntoState() throws Exception {
        ExperienceRuntimeToolStateInterceptor interceptor = new ExperienceRuntimeToolStateInterceptor();
        OverAllState state = new OverAllState(Map.of());

        invokeMergeDirectGroundings(interceptor, state, """
                {
                  "query":"发布流程",
                  "directGroundings":[
                    {
                      "id":"common-1",
                      "experienceType":"COMMON",
                      "title":"术语解释",
                      "content":"Aone 是产研协同平台。",
                      "disclosureStrategy":"DIRECT",
                      "score":0.95
                    }
                  ]
                }
                """, ExperienceRuntimeModelInterceptor.SEARCH_EXP_TOOL_NAME);

        Object stored = state.value(CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS).orElse(List.of());
        assertTrue(stored instanceof List<?> list);
        assertEquals(1, ((List<?>) stored).size());
        DirectExperienceGrounding grounding = (DirectExperienceGrounding) ((List<?>) stored).get(0);
        assertEquals("common-1", grounding.getId());
        assertEquals("Aone 是产研协同平台。", grounding.getContent());
    }

    @Test
    void shouldIgnoreIneligibleDirectGroundingFromReadResponse() throws Exception {
        ExperienceRuntimeToolStateInterceptor interceptor = new ExperienceRuntimeToolStateInterceptor();
        OverAllState state = new OverAllState(Map.of());

        invokeMergeDirectGroundings(interceptor, state, """
                {
                  "found":true,
                  "id":"react-1",
                  "experienceType":"REACT",
                  "title":"流程经验",
                  "content":"%s",
                  "disclosureStrategy":"DIRECT",
                  "score":0.99
                }
                """.formatted("x".repeat(501)), ExperienceRuntimeModelInterceptor.READ_EXP_TOOL_NAME);

        Object stored = state.value(CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS).orElse(List.of());
        assertEquals(List.of(), stored);
    }

    private void invokeMergeDirectGroundings(ExperienceRuntimeToolStateInterceptor interceptor,
                                             OverAllState state,
                                             String json,
                                             String toolName) throws Exception {
        Method method = ExperienceRuntimeToolStateInterceptor.class
                .getDeclaredMethod("mergeDirectGroundings", OverAllState.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(interceptor, state, json, toolName);
    }
}
