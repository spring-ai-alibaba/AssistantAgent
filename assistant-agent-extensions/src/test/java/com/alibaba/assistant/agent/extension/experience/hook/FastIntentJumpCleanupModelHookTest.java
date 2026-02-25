/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.extension.experience.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FastIntentJumpCleanupModelHookTest {

	@Test
	void shouldClearJumpToWhenFastIntentHitAndJumpToTool() {
		FastIntentJumpCleanupModelHook hook = new FastIntentJumpCleanupModelHook();
		OverAllState state = mock(OverAllState.class);

		when(state.value("messages")).thenReturn(Optional.of(List.of()));
		when(state.value("fast_intent")).thenReturn(Optional.of(Map.of("hit", true)));
		when(state.value("jump_to")).thenReturn(Optional.of(JumpTo.tool));

		Map<String, Object> updates = hook.beforeModel(state, null).join();

		assertThat(updates).containsKey("jump_to");
		assertThat(updates.get("jump_to")).isNull();
	}

	@Test
	void shouldClearJumpToWhenFastIntentHitAndJumpToToolString() {
		FastIntentJumpCleanupModelHook hook = new FastIntentJumpCleanupModelHook();
		OverAllState state = mock(OverAllState.class);

		when(state.value("messages")).thenReturn(Optional.of(List.of()));
		when(state.value("fast_intent")).thenReturn(Optional.of(Map.of("hit", true)));
		when(state.value("jump_to")).thenReturn(Optional.of("tool"));

		Map<String, Object> updates = hook.beforeModel(state, null).join();

		assertThat(updates).containsKey("jump_to");
		assertThat(updates.get("jump_to")).isNull();
	}

	@Test
	void shouldNotChangeStateWhenFastIntentNotHit() {
		FastIntentJumpCleanupModelHook hook = new FastIntentJumpCleanupModelHook();
		OverAllState state = mock(OverAllState.class);

		when(state.value("messages")).thenReturn(Optional.of(List.of()));
		when(state.value("fast_intent")).thenReturn(Optional.of(Map.of("hit", false)));
		when(state.value("jump_to")).thenReturn(Optional.of(JumpTo.tool));

		Map<String, Object> updates = hook.beforeModel(state, null).join();

		assertThat(updates).isEmpty();
	}

	@Test
	void shouldDirectReplyAndEndWhenWorkReportToolReturnsSlotMissing() {
		FastIntentJumpCleanupModelHook hook = new FastIntentJumpCleanupModelHook();
		OverAllState state = mock(OverAllState.class);

		String responseData = "{\"status\":\"SLOT_MISSING\",\"tool_name\":\"submit_office_work_report\",\"missing_fields\":[\"to_unames\"],\"message\":\"为了完成工作汇报提交，请提供：汇报对象姓名\"}";
		List<Message> messages = List.of(toolResponse("call_1", "submit_office_work_report", responseData));
		when(state.value("messages")).thenReturn(Optional.of(messages));
		when(state.value("fast_intent")).thenReturn(Optional.empty());

		Map<String, Object> updates = hook.beforeModel(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.end);
		assertThat(updates).containsKey("messages");
		assertThat((List<?>) updates.get("messages")).hasSize(1);
		assertThat(((Message) ((List<?>) updates.get("messages")).get(0)).getText())
				.contains("为了完成工作汇报提交");
	}

	@Test
	void shouldDirectReplyAndEndWhenLeaveToolReturnsWaitConfirm() {
		FastIntentJumpCleanupModelHook hook = new FastIntentJumpCleanupModelHook();
		OverAllState state = mock(OverAllState.class);

		String responseData = "{\"status\":\"WAIT_CONFIRM\",\"tool_name\":\"submit_leave\",\"confirmation_required\":true,\"message\":\"请确认是否提交请假申请\"}";
		List<Message> messages = List.of(toolResponse("call_1", "submit_leave", responseData));
		when(state.value("messages")).thenReturn(Optional.of(messages));
		when(state.value("fast_intent")).thenReturn(Optional.empty());

		Map<String, Object> updates = hook.beforeModel(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.end);
		assertThat(((Message) ((List<?>) updates.get("messages")).get(0)).getText())
				.contains("请确认是否提交请假申请");
	}

	private ToolResponseMessage toolResponse(String id, String toolName, String responseData) {
		return ToolResponseMessage.builder().responses(List.of(
				new ToolResponseMessage.ToolResponse(id, toolName, responseData)
		)).build();
	}
}
