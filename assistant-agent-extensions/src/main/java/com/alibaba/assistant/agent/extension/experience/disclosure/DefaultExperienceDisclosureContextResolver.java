package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.util.StringUtils;

/**
 * Default disclosure context resolver for generic Assistant Agent usage.
 *
 * <p>This fallback keeps the extension usable out of the box by resolving the query from
 * the raw {@code input} field and by deriving tenant context from common state/metadata
 * keys when no business-specific resolver is provided.
 */
public class DefaultExperienceDisclosureContextResolver implements ExperienceDisclosureContextResolver {

    @Override
    public String resolveQuery(OverAllState state, RunnableConfig config) {
        if (state == null) {
            return null;
        }
        return state.value("input", String.class).orElse(null);
    }

    @Override
    public ExperienceQueryContext buildQueryContext(OverAllState state, RunnableConfig config, String userQuery) {
        ExperienceQueryContext context = new ExperienceQueryContext();
        context.setUserQuery(userQuery);
        if (state != null) {
            state.value("tenant_id", String.class).ifPresent(context::setTenantId);
            if (!StringUtils.hasText(context.getTenantId())) {
                state.value("user_id", String.class).ifPresent(context::setTenantId);
            }
        }
        if (!StringUtils.hasText(context.getTenantId()) && config != null) {
            config.metadata("tenant_id").ifPresent(value -> context.setTenantId(String.valueOf(value)));
            if (!StringUtils.hasText(context.getTenantId())) {
                config.metadata("user_id").ifPresent(value -> context.setTenantId(String.valueOf(value)));
            }
        }
        return context;
    }
}
