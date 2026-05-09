package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.DirectExperienceGrounding;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ReadExpResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallExecutionContext;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists disclosure tool results back into agent state.
 *
 * <p>After {@code search_exp} or {@code read_exp} returns, this interceptor updates the
 * current round's direct-tool allowlist and caches read details so later runtime stages
 * can reuse the disclosed experience information without re-querying storage.
 */
public class ExperienceRuntimeToolStateInterceptor extends ToolInterceptor {

    private static final int DIRECT_CONTENT_MAX_LENGTH = 500;
    private static final double DIRECT_CONFIDENCE_THRESHOLD = 0.8D;

    @Override
    public String getName() {
        return "ExperienceRuntimeToolStateInterceptor";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        ToolCallResponse response = handler.call(request);
        if (!ExperienceRuntimeModelInterceptor.SEARCH_EXP_TOOL_NAME.equals(request.getToolName())
                && !ExperienceRuntimeModelInterceptor.READ_EXP_TOOL_NAME.equals(request.getToolName())) {
            return response;
        }

        Optional<ToolCallExecutionContext> executionContext = request.getExecutionContext();
        if (executionContext.isEmpty()) {
            return response;
        }
        OverAllState state = executionContext.get().state();
        mergeDirectToolNames(state, response.getResult(), request.getToolName());
        maybeCacheReadDetail(state, response.getResult(), request.getToolName());
        mergeDirectGroundings(state, response.getResult(), request.getToolName());
        return response;
    }

    private void recordReadExpId(OverAllState state, String id) {
        if (state == null || id == null || id.isBlank()) {
            return;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Object existing = state.value(CodeactStateKeys.EXPERIENCE_READ_EXP_IDS).orElse(null);
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    ids.add(String.valueOf(item));
                }
            }
        }
        ids.add(id);
        state.updateState(Map.of(CodeactStateKeys.EXPERIENCE_READ_EXP_IDS, new ArrayList<>(ids)));
    }

    private void mergeDirectToolNames(OverAllState state, String json, String toolName) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        Object existing = state.value(CodeactStateKeys.EXPERIENCE_ALLOWED_REACT_TOOL_NAMES).orElse(null);
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    merged.add(String.valueOf(item));
                }
            }
        }

        if (ExperienceRuntimeModelInterceptor.SEARCH_EXP_TOOL_NAME.equals(toolName)) {
            JSONObject root = JSON.parseObject(json);
            JSONObject candidates = root.getJSONObject("candidates");
            if (candidates != null) {
                com.alibaba.fastjson.JSONArray toolCandidatesArray = candidates.getJSONArray("toolCandidates");
                if (toolCandidatesArray != null) {
                    List<JSONObject> toolCandidates = toolCandidatesArray.toJavaList(JSONObject.class);
                    for (JSONObject candidate : toolCandidates) {
                        String invocationPath = candidate.getString("toolInvocationPath");
                        if (isReactDirect(invocationPath)) {
                            String callableToolName = candidate.getString("callableToolName");
                            if (callableToolName != null && !callableToolName.isBlank()) {
                                merged.add(callableToolName);
                            }
                        }
                    }
                }
            }
        }

        if (ExperienceRuntimeModelInterceptor.READ_EXP_TOOL_NAME.equals(toolName)) {
            JSONObject root = JSON.parseObject(json);
            String invocationPath = root.getString("toolInvocationPath");
            if (isReactDirect(invocationPath)) {
                String callableToolName = root.getString("callableToolName");
                if (callableToolName != null && !callableToolName.isBlank()) {
                    merged.add(callableToolName);
                }
            }
        }

        state.updateState(Map.of(CodeactStateKeys.EXPERIENCE_ALLOWED_REACT_TOOL_NAMES, new ArrayList<>(merged)));
    }

    private void maybeCacheReadDetail(OverAllState state, String json, String toolName) {
        if (!ExperienceRuntimeModelInterceptor.READ_EXP_TOOL_NAME.equals(toolName)) {
            return;
        }
        ReadExpResponse response = JSON.parseObject(json, ReadExpResponse.class);
        if (!response.isFound() || response.getId() == null) {
            return;
        }

        Map<String, Object> cache = new LinkedHashMap<>();
        Object existing = state.value(CodeactStateKeys.EXPERIENCE_DETAIL_CACHE).orElse(null);
        if (existing instanceof Map<?, ?> existingMap) {
            for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                if (entry.getKey() != null) {
                    cache.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        cache.put(response.getId(), response);
        state.updateState(Map.of(CodeactStateKeys.EXPERIENCE_DETAIL_CACHE, cache));
        recordReadExpId(state, response.getId());
    }

    private void mergeDirectGroundings(OverAllState state, String json, String toolName) {
        LinkedHashMap<String, DirectExperienceGrounding> merged = new LinkedHashMap<>();
        Object existing = state.value(CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS).orElse(null);
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof DirectExperienceGrounding grounding && grounding.getId() != null) {
                    merged.put(grounding.getId(), grounding);
                }
            }
        }

        if (ExperienceRuntimeModelInterceptor.SEARCH_EXP_TOOL_NAME.equals(toolName)) {
            JSONObject root = JSON.parseObject(json);
            com.alibaba.fastjson.JSONArray directGroundings = root.getJSONArray("directGroundings");
            if (directGroundings != null) {
                List<DirectExperienceGrounding> groundings =
                        directGroundings.toJavaList(DirectExperienceGrounding.class);
                for (DirectExperienceGrounding grounding : groundings) {
                    if (grounding != null && grounding.getId() != null) {
                        merged.put(grounding.getId(), grounding);
                    }
                }
            }
        }

        if (ExperienceRuntimeModelInterceptor.READ_EXP_TOOL_NAME.equals(toolName)) {
            ReadExpResponse response = JSON.parseObject(json, ReadExpResponse.class);
            if (isDirectGroundingEligible(response)) {
                DirectExperienceGrounding grounding = new DirectExperienceGrounding();
                grounding.setId(response.getId());
                grounding.setExperienceType(response.getExperienceType());
                grounding.setTitle(response.getTitle());
                grounding.setDescription(response.getDescription());
                grounding.setContent(response.getContent());
                grounding.setDisclosureStrategy(response.getDisclosureStrategy());
                grounding.setScore(response.getScore());
                grounding.setToolInvocationPath(response.getToolInvocationPath());
                grounding.setCallableToolName(response.getCallableToolName());
                merged.put(grounding.getId(), grounding);
            }
        }

        state.updateState(Map.of(CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS, new ArrayList<>(merged.values())));
    }

    private boolean isReactDirect(String invocationPath) {
        return "REACT_DIRECT".equals(invocationPath);
    }

    private boolean isDirectGroundingEligible(ReadExpResponse response) {
        if (response == null || !response.isFound()) {
            return false;
        }
        if (!"DIRECT".equals(response.getDisclosureStrategy())) {
            return false;
        }
        String content = response.getContent();
        if (content == null || content.isBlank() || content.length() > DIRECT_CONTENT_MAX_LENGTH) {
            return false;
        }
        Double score = response.getScore();
        return score == null || score >= DIRECT_CONFIDENCE_THRESHOLD;
    }
}
