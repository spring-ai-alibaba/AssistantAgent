package com.alibaba.assistant.agent.extension.experience.tool;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Adapts {@link CodeactTool} instances into React-stage {@link FunctionToolCallback}s.
 *
 * <p>Progressive disclosure uses this wrapper when a TOOL experience is allowed to be
 * called directly in the React loop. The wrapped callback preserves the original
 * CodeAct tool implementation and only changes how the runtime exposes it to the model.
 */
public final class ReactDirectCodeactToolCallbackWrapper {

    private ReactDirectCodeactToolCallbackWrapper() {
    }

    @SuppressWarnings("unchecked")
    public static FunctionToolCallback<Map<String, Object>, String> wrap(CodeactTool codeactTool) {
        String reactToolName = resolveReactToolName(codeactTool);
        BiFunction<Map<String, Object>, ToolContext, String> function = (mapInput, toolContext) ->
                codeactTool.call(JSON.toJSONString(mapInput), toolContext);

        return FunctionToolCallback
                .<Map<String, Object>, String>builder(reactToolName, function)
                .description(codeactTool.getToolDefinition().description())
                .inputType((Class<Map<String, Object>>) (Class<?>) Map.class)
                .inputSchema(codeactTool.getToolDefinition().inputSchema())
                .build();
    }

    public static List<FunctionToolCallback<Map<String, Object>, String>> wrapAll(List<CodeactTool> codeactTools) {
        List<FunctionToolCallback<Map<String, Object>, String>> callbacks = new ArrayList<>();
        if (codeactTools == null) {
            return callbacks;
        }
        for (CodeactTool codeactTool : codeactTools) {
            if (hasReactToolName(codeactTool)) {
                callbacks.add(wrap(codeactTool));
            }
        }
        return callbacks;
    }

    public static boolean hasReactToolName(CodeactTool codeactTool) {
        return StringUtils.hasText(resolveReactToolName(codeactTool));
    }

    public static String resolveReactToolName(CodeactTool codeactTool) {
        if (codeactTool == null || codeactTool.getToolDefinition() == null) {
            return null;
        }
        String runtimeToolName = codeactTool.getToolDefinition().name();
        if (codeactTool.getCodeactMetadata() != null) {
            String invocationTemplate = codeactTool.getCodeactMetadata().codeInvocationTemplate();
            if (StringUtils.hasText(invocationTemplate) && invocationTemplate.contains("(")) {
                runtimeToolName = invocationTemplate.substring(0, invocationTemplate.indexOf("("));
            }
            String targetClassName = codeactTool.getCodeactMetadata().targetClassName();
            if (StringUtils.hasText(targetClassName) && StringUtils.hasText(runtimeToolName)) {
                return targetClassName + "." + runtimeToolName;
            }
        }
        return null;
    }
}
