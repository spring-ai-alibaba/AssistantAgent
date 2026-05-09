package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.util.StringUtils;

/**
 * Strategy interface for resolving disclosure query inputs from runtime state.
 *
 * <p>The extension layer owns the disclosure runtime itself, while each business agent
 * can provide its own resolver to decide which query to prefetch and which tenant/user
 * context should be attached to retrieval.
 */
public interface ExperienceDisclosureContextResolver {

    String resolveQuery(OverAllState state, RunnableConfig config);

    ExperienceQueryContext buildQueryContext(OverAllState state, RunnableConfig config, String userQuery);

    default boolean shouldPrefetch(OverAllState state, RunnableConfig config, String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        Integer currentRound = state != null ? state.value("current_round", Integer.class).orElse(0) : 0;
        return currentRound == null || currentRound <= 0;
    }
}
