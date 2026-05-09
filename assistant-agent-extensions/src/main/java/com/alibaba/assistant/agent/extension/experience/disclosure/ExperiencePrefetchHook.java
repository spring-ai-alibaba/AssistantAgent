package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ExperienceCandidateCard;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.PrefetchedExperienceSnapshot;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Prioritized;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Before-agent hook that performs first-round experience prefetch.
 *
 * <p>On the first React round it writes lightweight grouped candidates into state so the
 * prompt can disclose likely relevant experiences up front, and it seeds the allowlist
 * of direct-callable React tools inferred from TOOL candidates.
 */
@HookPositions(HookPosition.BEFORE_AGENT)
public class ExperiencePrefetchHook extends AgentHook implements Prioritized {

    private static final Logger log = LoggerFactory.getLogger(ExperiencePrefetchHook.class);

    private static final int PREFETCH_HOOK_ORDER = 150;

    private final ExperienceDisclosureService experienceDisclosureService;
    private final ExperienceDisclosureContextResolver contextResolver;
    private final ExperienceExtensionProperties properties;

    public ExperiencePrefetchHook(ExperienceDisclosureService experienceDisclosureService,
                                  ExperienceDisclosureContextResolver contextResolver,
                                  ExperienceExtensionProperties properties) {
        this.experienceDisclosureService = experienceDisclosureService;
        this.contextResolver = contextResolver;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "ExperiencePrefetchHook";
    }

    @Override
    public int getOrder() {
        return PREFETCH_HOOK_ORDER;
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of();
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        String query = contextResolver.resolveQuery(state, config);
        log.info("ExperiencePrefetchHook#beforeAgent - reason=开始预取经验, query={}, experienceEnabled={}",
                query, properties.isEnabled());

        PrefetchedExperienceSnapshot snapshot = new PrefetchedExperienceSnapshot();
        snapshot.setQuery(query);
        if (properties.isEnabled() && contextResolver.shouldPrefetch(state, config, query)) {
            ExperienceQueryContext queryContext = contextResolver.buildQueryContext(state, config, query);
            log.info("ExperiencePrefetchHook#beforeAgent - reason=执行预取, tenantId={}", queryContext.getTenantId());
            snapshot = experienceDisclosureService.prefetch(query, queryContext);
            log.info("ExperiencePrefetchHook#beforeAgent - reason=预取完成, status={}, " +
                    "commonCandidates={}, reactCandidates={}, toolCandidates={}, directGroundings={}",
                    snapshot.getStatus(),
                    snapshot.getCandidates().getCommonCandidates().size(),
                    snapshot.getCandidates().getReactCandidates().size(),
                    snapshot.getCandidates().getToolCandidates().size(),
                    snapshot.getDirectGroundings().size());
        } else {
            snapshot.setStatus(ExperienceDisclosurePayloads.PrefetchStatus.SKIPPED);
            boolean shouldPrefetch = contextResolver.shouldPrefetch(state, config, query);
            log.info("ExperiencePrefetchHook#beforeAgent - reason=跳过预取, " +
                    "experienceEnabled={}, shouldPrefetch={}, queryPresent={}",
                    properties.isEnabled(), shouldPrefetch, query != null);
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(CodeactStateKeys.EXPERIENCE_PREFETCH_QUERY, snapshot.getQuery());
        updates.put(CodeactStateKeys.EXPERIENCE_PREFETCH_STATUS, snapshot.getStatus().name());
        updates.put(CodeactStateKeys.EXPERIENCE_PREFETCHED_CANDIDATES, snapshot.getCandidates());
        updates.put(CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS, snapshot.getDirectGroundings());
        updates.put(CodeactStateKeys.EXPERIENCE_DETAIL_CACHE, Map.of());
        updates.put(CodeactStateKeys.EXPERIENCE_ALLOWED_REACT_TOOL_NAMES,
                collectDirectToolNames(snapshot.getCandidates().getToolCandidates()));
        return CompletableFuture.completedFuture(updates);
    }

    private List<String> collectDirectToolNames(List<ExperienceCandidateCard> toolCandidates) {
        LinkedHashSet<String> toolNames = new LinkedHashSet<>();
        for (ExperienceCandidateCard toolCandidate : toolCandidates) {
            if (toolCandidate.getToolInvocationPath() == ExperienceDisclosurePayloads.ToolInvocationPath.REACT_DIRECT) {
                if (toolCandidate.getCallableToolName() != null && !toolCandidate.getCallableToolName().isBlank()) {
                    toolNames.add(toolCandidate.getCallableToolName());
                }
            }
        }
        return new ArrayList<>(toolNames);
    }
}
