package com.alibaba.assistant.agent.extension.reply.tools;

import com.alibaba.assistant.agent.common.tools.ReplyCodeactTool;
import com.alibaba.assistant.agent.extension.reply.config.ReplyToolConfig;
import com.alibaba.assistant.agent.extension.reply.model.ChannelExecutionContext;
import com.alibaba.assistant.agent.extension.reply.model.ParameterSchema;
import com.alibaba.assistant.agent.extension.reply.model.ReplyResult;
import com.alibaba.assistant.agent.extension.reply.spi.ReplyChannelDefinition;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BaseReplyCodeactToolTest {

	@Test
	void shouldCopyChannelMetadataIntoExecutionContextExtensions() {
		AtomicReference<ChannelExecutionContext> capturedContext = new AtomicReference<>();
		BaseReplyCodeactTool tool = new BaseReplyCodeactTool(
				"send_dingtalk_card",
				"Send a DingTalk card",
				new CapturingReplyChannel(capturedContext),
				new ReplyToolConfig(),
				null,
				ReplyCodeactTool.ReplyChannelType.PRIMARY);

		RunnableConfig runnableConfig = RunnableConfig.builder()
				.threadId("session_123")
				.addMetadata("user_id", "user_456")
				.addMetadata("trace_id", "trace_789")
				.addMetadata("channel_type", "DINGTALK_GROUP")
				.addMetadata("channel_id", "cid_001")
				.build();
		Map<String, Object> contextMap = new HashMap<>();
		contextMap.put(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY, runnableConfig);

		tool.call("{}", new ToolContext(contextMap));

		ChannelExecutionContext context = capturedContext.get();
		assertNotNull(context);
		assertEquals("session_123", context.getSessionId());
		assertEquals("user_456", context.getUserId());
		assertEquals("trace_789", context.getTraceId());
		assertEquals("DINGTALK_GROUP", context.getExtension("channel_type"));
		assertEquals("cid_001", context.getExtension("channel_id"));
	}

	private static final class CapturingReplyChannel implements ReplyChannelDefinition {

		private final AtomicReference<ChannelExecutionContext> capturedContext;

		private CapturingReplyChannel(AtomicReference<ChannelExecutionContext> capturedContext) {
			this.capturedContext = capturedContext;
		}

		@Override
		public String getChannelCode() {
			return "DINGTALK";
		}

		@Override
		public String getDescription() {
			return "test";
		}

		@Override
		public ParameterSchema getSupportedParameters() {
			return null;
		}

		@Override
		public ReplyResult execute(ChannelExecutionContext context, Map<String, Object> params) {
			capturedContext.set(context);
			return ReplyResult.success("ok");
		}
	}
}
