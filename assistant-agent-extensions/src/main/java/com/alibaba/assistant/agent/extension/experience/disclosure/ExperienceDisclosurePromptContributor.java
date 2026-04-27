package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.DirectExperienceGrounding;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ExperienceCandidateCard;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.GroupedExperienceCandidates;
import com.alibaba.assistant.agent.prompt.PromptContribution;
import com.alibaba.assistant.agent.prompt.PromptContributor;
import com.alibaba.assistant.agent.prompt.PromptContributorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Framework-level prompt contributor for experience progressive disclosure.
 *
 * <p>When prefetched experience candidates or direct groundings are present in the agent
 * state, this contributor injects a structured prompt that teaches the LLM how to work
 * with the disclosure system:
 * <ul>
 *   <li>What the three experience types (COMMON, REACT, TOOL) mean</li>
 *   <li>How DIRECT vs PROGRESSIVE disclosure works</li>
 *   <li>When and how to use {@code search_exp} and {@code read_exp} tools</li>
 *   <li>The current round's candidate cards and already-disclosed content</li>
 * </ul>
 *
 * <p>Business applications can override this bean via {@code @ConditionalOnMissingBean}
 * to provide a customised disclosure prompt (e.g. with domain-specific guidance).
 */
public class ExperienceDisclosurePromptContributor implements PromptContributor {

    private static final Logger log = LoggerFactory.getLogger(ExperienceDisclosurePromptContributor.class);

    private static final int DEFAULT_PRIORITY = 18;
    private static final int MAX_CARDS_PER_SECTION = 3;
    private static final int MAX_DESCRIPTION_LENGTH = 100;
    private static final int MAX_DIRECT_CONTENT_LENGTH = 500;

    private final int priority;

    public ExperienceDisclosurePromptContributor() {
        this(DEFAULT_PRIORITY);
    }

    public ExperienceDisclosurePromptContributor(int priority) {
        this.priority = priority;
    }

    @Override
    public String getName() {
        return "ExperienceDisclosurePromptContributor";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean shouldContribute(PromptContributorContext context) {
        boolean hasCandidates = context.getAttribute(
                        CodeactStateKeys.EXPERIENCE_PREFETCHED_CANDIDATES, GroupedExperienceCandidates.class)
                .map(this::hasAnyCandidates)
                .orElse(false);
        boolean hasDirectGroundings = context.getAttribute(
                        CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS, List.class)
                .map(this::normalizeDirectGroundings)
                .map(list -> !list.isEmpty())
                .orElse(false);
        boolean result = hasCandidates || hasDirectGroundings;
        log.info("ExperienceDisclosurePromptContributor#shouldContribute - " +
                "hasCandidates={}, hasDirectGroundings={}, result={}", hasCandidates, hasDirectGroundings, result);
        return result;
    }

    @Override
    public PromptContribution contribute(PromptContributorContext context) {
        GroupedExperienceCandidates candidates = context.getAttribute(
                CodeactStateKeys.EXPERIENCE_PREFETCHED_CANDIDATES, GroupedExperienceCandidates.class).orElse(null);
        List<DirectExperienceGrounding> directGroundings = context
                .getAttribute(CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS, List.class)
                .map(this::normalizeDirectGroundings)
                .orElse(List.of());
        if ((candidates == null || !hasAnyCandidates(candidates)) && directGroundings.isEmpty()) {
            return PromptContribution.empty();
        }
        return PromptContribution.builder()
                .append(new UserMessage(buildPrompt(candidates, directGroundings)))
                .build();
    }

    // ─── Prompt construction ──────────────────────────────────────────

    /**
     * Builds the complete disclosure prompt. Subclasses may override this to customise
     * the prompt text while reusing the candidate rendering helpers.
     */
    protected String buildPrompt(GroupedExperienceCandidates candidates,
                                 List<DirectExperienceGrounding> directGroundings) {
        StringBuilder sb = new StringBuilder();
        Set<String> disclosedDirectIds = collectDirectIds(directGroundings);

        sb.append("<experience_disclosure>\n");
        appendGuidanceText(sb);
        appendDirectGroundings(sb, directGroundings);
        if (candidates != null) {
            appendSection(sb, "COMMON grounding candidates",
                    filterAlreadyDisclosedCandidates(candidates.getCommonCandidates(), disclosedDirectIds));
            appendSection(sb, "REACT workflow candidates",
                    filterAlreadyDisclosedCandidates(candidates.getReactCandidates(), disclosedDirectIds));
            appendSection(sb, "TOOL capability candidates",
                    filterAlreadyDisclosedCandidates(candidates.getToolCandidates(), disclosedDirectIds));
        }
        sb.append("</experience_disclosure>");
        return sb.toString();
    }

    /**
     * 追加披露机制的引导说明文本。
     * 子类可覆写以提供领域特定的说明。
     */
    protected void appendGuidanceText(StringBuilder sb) {
        sb.append("经验分为三类：COMMON 用于解释概念/产品术语，REACT 用于提供流程与策略参考，TOOL 用于能力判断、工具选择与调用路径判断。\n");
        sb.append("披露方式分为两种：`DIRECT` 表示内容已满足高置信短文本条件，可直接利用；`PROGRESSIVE` 表示当前只给候选卡，需要完整正文时调用 `read_exp`。\n");
        sb.append("渐进披露分三层：L1 候选卡（本块内容） → L2 `read_exp(id)` 返回 content + referenceManifest + assetManifest → L3 `read_exp_doc(id, paths)` 仅按需读取 referenceManifest 中的某些参考文档完整内容。\n");
        sb.append("调用 `read_exp_doc` 前必须先 `read_exp(id)`，否则会返回错误：\"请先 read_exp(<id>)，再 read_exp_doc\"；单次最多读取若干个路径，超出会被截断。\n");
        sb.append("`read_exp_doc` 仅能读取 referenceManifest 列出的文档；assetManifest 列出的脚本/资源文件不可通过 `read_exp_doc` 访问 —— 它们已被预先物化到沙箱 `/workspace/<path>` 下，仅在沙箱内通过工具执行（cat/python/bash 等）读取。\n");
        sb.append("优先复用已披露的 DIRECT 内容，再从候选中选择 id；只有当前候选明显不足或缺少方向时，再调用 `search_exp`。\n");
        sb.append("TOOL 候选如果标记为 `REACT_DIRECT`，表示它具备被直接作为 React function call 调用的资格；");
        sb.append("但只有在该单个工具即可直接完成任务时，才优先 direct call。");
        sb.append("若需要多个工具配合、顺序编排、条件分支、循环重试或结果再加工，请改走 `write_code` + `execute_code`。");
        sb.append("若标记为 `CODE_ONLY`，则只能通过 `write_code` + `execute_code` 使用。\n\n");
    }

    // ─── Section rendering helpers ────────────────────────────────────

    protected void appendDirectGroundings(StringBuilder sb, List<DirectExperienceGrounding> directGroundings) {
        if (directGroundings == null || directGroundings.isEmpty()) {
            return;
        }
        sb.append("DIRECT grounding already available:\n");
        for (DirectExperienceGrounding grounding : directGroundings) {
            sb.append("- id=").append(grounding.getId())
                    .append(", type=").append(grounding.getExperienceType())
                    .append(", title=").append(safe(grounding.getTitle()));
            if (grounding.getScore() != null) {
                sb.append(", score=").append(grounding.getScore());
            }
            if (grounding.getToolInvocationPath() != null) {
                sb.append(", invocationPath=").append(grounding.getToolInvocationPath());
            }
            if (grounding.getCallableToolName() != null && !grounding.getCallableToolName().isBlank()) {
                sb.append(", toolName=").append(grounding.getCallableToolName());
            }
            sb.append("\n");
            sb.append("  content=").append(trimDirectContent(grounding.getContent())).append("\n");
        }
        sb.append("\n");
    }

    protected void appendSection(StringBuilder sb, String title, List<ExperienceCandidateCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        sb.append(title).append(":\n");
        int limit = Math.min(cards.size(), MAX_CARDS_PER_SECTION);
        for (int i = 0; i < limit; i++) {
            ExperienceCandidateCard card = cards.get(i);
            sb.append("- id=").append(card.getId())
                    .append(", type=").append(card.getExperienceType())
                    .append(", title=").append(safe(card.getTitle()));
            if (card.getDescription() != null && !card.getDescription().isBlank()) {
                String desc = card.getDescription();
                if (desc.length() > MAX_DESCRIPTION_LENGTH) {
                    desc = desc.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
                }
                sb.append(", description=").append(desc);
            }
            if (card.getToolInvocationPath() != null) {
                sb.append(", invocationPath=").append(card.getToolInvocationPath());
            }
            if (card.getCallableToolName() != null && !card.getCallableToolName().isBlank()) {
                sb.append(", toolName=").append(card.getCallableToolName());
            }
            sb.append("\n");
        }
        if (cards.size() > limit) {
            sb.append("- ... ").append(cards.size() - limit).append(" more candidates omitted\n");
        }
        sb.append("\n");
    }

    // ─── Utilities ────────────────────────────────────────────────────

    private boolean hasAnyCandidates(GroupedExperienceCandidates candidates) {
        return !(candidates.getCommonCandidates().isEmpty()
                && candidates.getReactCandidates().isEmpty()
                && candidates.getToolCandidates().isEmpty());
    }

    private Set<String> collectDirectIds(List<DirectExperienceGrounding> directGroundings) {
        Set<String> directIds = new LinkedHashSet<>();
        if (directGroundings == null || directGroundings.isEmpty()) {
            return directIds;
        }
        for (DirectExperienceGrounding grounding : directGroundings) {
            if (grounding != null && grounding.getId() != null && !grounding.getId().isBlank()) {
                directIds.add(grounding.getId());
            }
        }
        return directIds;
    }

    private List<ExperienceCandidateCard> filterAlreadyDisclosedCandidates(
            List<ExperienceCandidateCard> cards, Set<String> disclosedDirectIds) {
        if (cards == null || cards.isEmpty() || disclosedDirectIds == null || disclosedDirectIds.isEmpty()) {
            return cards;
        }
        return cards.stream()
                .filter(card -> card != null)
                .filter(card -> !disclosedDirectIds.contains(card.getId()))
                .toList();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimDirectContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_DIRECT_CONTENT_LENGTH) {
            return content.replace("\n", "\\n");
        }
        return content.substring(0, MAX_DIRECT_CONTENT_LENGTH).replace("\n", "\\n") + "...";
    }

    @SuppressWarnings("unchecked")
    private List<DirectExperienceGrounding> normalizeDirectGroundings(List<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<DirectExperienceGrounding> normalized = new java.util.ArrayList<>();
        for (Object value : values) {
            if (value instanceof DirectExperienceGrounding grounding) {
                normalized.add(grounding);
            }
        }
        return normalized;
    }
}
