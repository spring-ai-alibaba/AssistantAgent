package com.alibaba.assistant.agent.extension.experience.fastintent;

import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CodeFastIntentSupport - CODE FastIntent 命中判断与产物提取（只负责“命中选择”，不负责注册/执行代码）
 *
 * <p>设计目标：让 write_code / write_condition_code 等多个入口复用同一套 fast-intent 命中逻辑，避免逻辑重复
 */
public class CodeFastIntentSupport {

    private static final Logger log = LoggerFactory.getLogger(CodeFastIntentSupport.class);

    private final ExperienceProvider experienceProvider;
    private final ExperienceExtensionProperties properties;
    private final FastIntentService fastIntentService;

    public CodeFastIntentSupport(ExperienceProvider experienceProvider,
                                 ExperienceExtensionProperties properties,
                                 FastIntentService fastIntentService) {
        this.experienceProvider = experienceProvider;
        this.properties = properties;
        this.fastIntentService = fastIntentService;
    }

    public Optional<Hit> tryHit(ToolContext toolContext, Map<String, Object> toolRequest, String language) {
        try {
            if (experienceProvider == null || properties == null || fastIntentService == null) {
                return Optional.empty();
            }
            if (!properties.isEnabled()
                    || !properties.isCodeExperienceEnabled()
                    || !properties.isFastIntentEnabled()
                    || !properties.isFastIntentCodeEnabled()) {
                return Optional.empty();
            }

            OverAllState state = (OverAllState) toolContext.getContext()
                    .get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);
            RunnableConfig config = (RunnableConfig) toolContext.getContext()
                    .get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);

            String input = state != null ? state.value("input", String.class).orElse(null) : null;
            Map<String, Object> md = config != null ? config.metadata().orElse(Map.of()) : Map.of();
            @SuppressWarnings("unchecked")
            List<Message> messages = state != null ? state.value("messages", List.class).orElse(List.of()) : List.of();

            FastIntentContext ctx = new FastIntentContext(input, messages, md, state, toolRequest);

            ExperienceQueryContext queryContext = buildQueryContext(state, config, language, input);
            // 使用 write_code 的 requirement 参数作为搜索文本，提升向量搜索召回率
            String requirement = toolRequest != null ? (String) toolRequest.get("requirement") : null;
            if (StringUtils.hasText(requirement)) {
                queryContext.setUserQuery(requirement);
            }
            ExperienceQuery query = new ExperienceQuery(ExperienceType.CODE);
            query.setText(requirement);
            query.setLimit(Math.max(40, properties.getMaxItemsPerQuery()));

            log.info("CodeFastIntentSupport#tryHit - reason=querying code experiences, text={}, limit={}",
                    requirement != null ? (requirement.length() > 50 ? requirement.substring(0, 50) + "..." : requirement) : "null",
                    query.getLimit());

            List<Experience> candidates = experienceProvider.query(query, queryContext);
            Optional<Experience> bestOpt = fastIntentService.selectBestMatch(candidates, ctx);
            if (bestOpt.isEmpty()) {
                return Optional.empty();
            }

            Experience best = bestOpt.get();
            ExperienceArtifact.CodeArtifact codeArtifact = best.getArtifact() != null ? best.getArtifact().getCode() : null;
            if (codeArtifact == null || codeArtifact.getCode() == null || codeArtifact.getCode().isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new Hit(best, codeArtifact));

        } catch (Exception e) {
            // fail-open: hit 判断失败则回退到正常 LLM 流程
            log.warn("CodeFastIntentSupport#tryHit - reason=fast-intent failed, fallback to normal flow, error={}", e.getMessage());
            return Optional.empty();
        }
    }

    private ExperienceQueryContext buildQueryContext(OverAllState state, RunnableConfig config, String language, String userQuery) {
        ExperienceQueryContext queryContext = new ExperienceQueryContext();
        // 关键修复：设置userQuery，用于向量搜索
        if (userQuery != null && !userQuery.isBlank()) {
            queryContext.setUserQuery(userQuery);
        }
        if (state != null) {
            state.value("user_id", String.class).ifPresent(queryContext::setUserId);
            state.value("project_id", String.class).ifPresent(queryContext::setProjectId);
            state.value("task_type", String.class).ifPresent(queryContext::setTaskType);
        }
        if (config != null) {
            config.metadata("agent_name").ifPresent(name -> queryContext.setAgentName(String.valueOf(name)));
            config.metadata("task_type").ifPresent(type -> queryContext.setTaskType(String.valueOf(type)));
        }
        if (language != null && !language.isBlank()) {
            queryContext.setLanguage(language);
        }
        return queryContext;
    }

    public static FastIntentConfig.FastIntentFallback getOnRegisterFallback(Experience experience) {
        if (experience == null || experience.getFastIntentConfig() == null || experience.getFastIntentConfig().getOnMatch() == null) {
            return FastIntentConfig.FastIntentFallback.REFERENCE_ONLY;
        }
        FastIntentConfig.FastIntentFallback fb = experience.getFastIntentConfig().getOnMatch().getFallback();
        return fb != null ? fb : FastIntentConfig.FastIntentFallback.REFERENCE_ONLY;
    }

    public static Map<String, Object> toolReqOf(String requirement, String functionName, List<String> parameters) {
        Map<String, Object> toolReq = new HashMap<>();
        toolReq.put("requirement", requirement);
        toolReq.put("functionName", functionName);
        toolReq.put("parameters", parameters != null ? parameters : List.of());
        return toolReq;
    }

    public record Hit(Experience experience, ExperienceArtifact.CodeArtifact codeArtifact) {
        public String code() {
            return codeArtifact != null ? codeArtifact.getCode() : null;
        }
    }
}


