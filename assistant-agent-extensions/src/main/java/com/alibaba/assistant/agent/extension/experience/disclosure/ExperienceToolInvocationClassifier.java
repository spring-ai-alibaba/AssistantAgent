package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.extension.experience.config.ExperienceExtensionProperties;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ToolInvocationPath;

/**
 * Classifies TOOL experiences into React-direct or code-only invocation paths.
 *
 * <p>The classifier reads TOOL metadata plus the configured allowlist and tells the
 * runtime whether a matched TOOL experience may be surfaced as a direct function call
 * or must stay on the write-code/execute-code path.
 */
public class ExperienceToolInvocationClassifier {

    public static final String TOOL_INVOCATION_PATH_PROPERTY = "toolInvocationPath";

    private final ExperienceExtensionProperties properties;

    public ExperienceToolInvocationClassifier(ExperienceExtensionProperties properties) {
        this.properties = properties;
    }

    public ToolInvocationPath classify(Experience experience) {
        ToolInvocationPath explicitInvocationPath = resolveExplicitInvocationPath(experience);
        if (explicitInvocationPath != null) {
            return explicitInvocationPath;
        }
        String toolName = resolveCallableToolName(experience);
        if (!StringUtils.hasText(toolName)) {
            return ToolInvocationPath.CODE_ONLY;
        }
        return isReactDirectTool(toolName) ? ToolInvocationPath.REACT_DIRECT : ToolInvocationPath.CODE_ONLY;
    }

    public boolean isReactDirectTool(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return false;
        }
        Set<String> configuredNames = getConfiguredReactDirectToolNames();
        if (configuredNames.contains(toolName)) {
            return true;
        }
        int lastDot = toolName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < toolName.length()) {
            return configuredNames.contains(toolName.substring(lastDot + 1));
        }
        return false;
    }

    public Set<String> getConfiguredReactDirectToolNames() {
        return new LinkedHashSet<>(properties.getReactDirectToolNames());
    }

    public String resolveCallableToolName(Experience experience) {
        if (experience == null) {
            return null;
        }
        ExperienceArtifact artifact = experience.getArtifact();
        if (artifact != null && artifact.getTool() != null && StringUtils.hasText(artifact.getTool().getCodeactToolName())) {
            return artifact.getTool().getCodeactToolName();
        }
        List<String> associatedTools = experience.getAssociatedTools();
        if (associatedTools != null) {
            for (String associatedTool : associatedTools) {
                if (StringUtils.hasText(associatedTool)) {
                    return associatedTool;
                }
            }
        }
        return null;
    }

    private ToolInvocationPath resolveExplicitInvocationPath(Experience experience) {
        if (experience == null || experience.getMetadata() == null) {
            return null;
        }
        Object configuredValue = experience.getMetadata().getProperty(TOOL_INVOCATION_PATH_PROPERTY);
        if (configuredValue == null) {
            return null;
        }
        String invocationPath = String.valueOf(configuredValue).trim();
        if (!StringUtils.hasText(invocationPath)) {
            return null;
        }
        try {
            return ToolInvocationPath.valueOf(invocationPath);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
