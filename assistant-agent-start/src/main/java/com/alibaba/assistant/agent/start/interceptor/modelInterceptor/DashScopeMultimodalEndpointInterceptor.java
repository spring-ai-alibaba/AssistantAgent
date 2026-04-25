package com.alibaba.assistant.agent.start.interceptor.modelInterceptor;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.env.Environment;

/**
 * 确保 Graph 传入的 {@link ModelRequest#getOptions()} 在合并后仍走 DashScope 多模态端点。
 *
 * <p>阿里云文档：qwen3.6-plus 等模型若用纯文本端点会报 {@code url error}。框架层可能下发
 * {@code multiModel=false} 的选项并覆盖 {@code application.yml} 默认值，本拦截器在调用链最前段纠偏。
 */
public class DashScopeMultimodalEndpointInterceptor extends ModelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DashScopeMultimodalEndpointInterceptor.class);

    private final Environment environment;

    public DashScopeMultimodalEndpointInterceptor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getName() {
        return "DashScopeMultimodalEndpointInterceptor";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        if (!Boolean.parseBoolean(
                environment.getProperty("spring.ai.dashscope.chat.options.multi-model", "true"))) {
            return handler.call(request);
        }
        ToolCallingChatOptions options = request.getOptions();
        if (options == null) {
            return handler.call(request);
        }
        try {
            DashScopeChatOptions dash;
            if (options instanceof DashScopeChatOptions dso) {
                dash = DashScopeChatOptions.fromOptions(dso);
            } else {
                dash = ModelOptionsUtils.copyToTarget(options, ToolCallingChatOptions.class, DashScopeChatOptions.class);
            }
            Boolean before = dash.getMultiModel();
            if (!Boolean.TRUE.equals(before)) {
                dash.setMultiModel(true);
                log.debug("DashScopeMultimodalEndpointInterceptor: set multiModel true (was {})", before);
                ModelRequest patched = ModelRequest.builder(request).options(dash).build();
                return handler.call(patched);
            }
        } catch (Exception e) {
            log.warn("DashScopeMultimodalEndpointInterceptor: failed to patch options, passing request through: {}",
                     e.toString());
        }
        return handler.call(request);
    }
}