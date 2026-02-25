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
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkReportDraftResumeReactHookTest {

	@Test
	void shouldRouteToToolAndExtractWorksWhenDraftIsSlotMissing() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{\"works\":\"今天完成了内江的工作\"}");
		List<Message> messages = List.of(toolResponse(
				"call_1",
				"submit_office_work_report",
				"{\"status\":\"SLOT_MISSING\",\"tool_name\":\"submit_office_work_report\",\"missing_fields\":[\"works\"]}"));
		OverAllState state = mockState(
				Map.of("capability_draft_submit_office_work_report_status", "SLOT_MISSING"),
				"今天完成了内江的工作",
				messages);

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
		AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
		AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
		assertThat(toolCall.name()).isEqualTo("submit_office_work_report");
		JSONObject args = JSON.parseObject(toolCall.arguments());
		assertThat(args.getString("works")).isEqualTo("今天完成了内江的工作");
	}

	@Test
	void shouldAttachConfirmedFlagWhenDraftWaitConfirmAndUserConfirmed() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{}");
		List<Message> messages = List.of(toolResponse(
				"call_1",
				"submit_office_work_report",
				"{\"status\":\"WAIT_CONFIRM\",\"tool_name\":\"submit_office_work_report\",\"preview\":{\"works\":\"已完成联调\"}}"));
		OverAllState state = mockState(
				Map.of("capability_draft_submit_office_work_report_status", "WAIT_CONFIRM"),
				"确认提交",
				messages);

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
		AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
		AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
		JSONObject args = JSON.parseObject(toolCall.arguments());
		assertThat(args.getBoolean("confirmed")).isTrue();
	}

	@Test
	void shouldDoNothingWhenNoActiveDraftStatus() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{}");
		OverAllState state = mockState(Map.of("capability_draft_submit_office_work_report_status", "IDLE"), "test", List.of());

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates).isEmpty();
	}

	@Test
	void shouldResumeWhenDraftStatusMissingButLatestToolResponseIsSlotMissing() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{\"works\":\"修复线上问题\"}");
		List<Message> messages = List.of(toolResponse(
				"call_1",
				"submit_office_work_report",
				"{\"status\":\"SLOT_MISSING\",\"tool_name\":\"submit_office_work_report\",\"missing_fields\":[\"works\"],\"message\":\"请补充字段\"}"));
		OverAllState state = mockState(Map.of(), "今日工作内容：修复线上问题", messages);

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
		AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
		AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
		JSONObject args = JSON.parseObject(toolCall.arguments());
		assertThat(args.getString("works")).isEqualTo("修复线上问题");
	}

	@Test
	void shouldCarryCollectedSlotsFromLatestToolResponseWhenResuming() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{\"to_unames\":\"张三\"}");
		List<Message> messages = List.of(toolResponse(
				"call_1",
				"submit_office_work_report",
				"{\"status\":\"SLOT_MISSING\",\"tool_name\":\"submit_office_work_report\",\"missing_fields\":[\"to_unames\"],\"collected_slots\":{\"works\":\"完成接口联调\",\"plans\":\"明日回归测试\"}}"));
		OverAllState state = mockState(Map.of(), "汇报对象姓名 张三", messages);

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
		AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
		AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
		JSONObject args = JSON.parseObject(toolCall.arguments());
		assertThat(args.getString("works")).isEqualTo("完成接口联调");
		assertThat(args.getString("plans")).isEqualTo("明日回归测试");
		assertThat(args.getString("to_unames")).isEqualTo("张三");
	}

	@Test
	void shouldCarryCollectedSlotsFromDraftStateWhenStatusIsActive() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{\"to_unames\":\"张三\"}");
		Map<String, Object> stateData = new LinkedHashMap<>();
		stateData.put("capability_draft_submit_office_work_report_status", "SLOT_MISSING");
		stateData.put("capability_draft_submit_office_work_report", Map.of("works", "完成接口联调", "plans", "明日回归测试"));
		List<Message> messages = List.of(toolResponse(
				"call_1",
				"submit_office_work_report",
				"{\"status\":\"SLOT_MISSING\",\"tool_name\":\"submit_office_work_report\",\"missing_fields\":[\"to_unames\"]}"));
		OverAllState state = mockState(stateData, "汇报对象姓名 张三", messages);

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
		AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
		AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
		JSONObject args = JSON.parseObject(toolCall.arguments());
		assertThat(args.getString("works")).isEqualTo("完成接口联调");
		assertThat(args.getString("plans")).isEqualTo("明日回归测试");
		assertThat(args.getString("to_unames")).isEqualTo("张三");
	}

	@Test
	void shouldCarryPreviewSlotsFromLatestToolResponseWhenWaitingConfirm() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{}");
		List<Message> messages = List.of(toolResponse(
				"call_1",
				"submit_office_work_report",
				"{\"status\":\"WAIT_CONFIRM\",\"tool_name\":\"submit_office_work_report\",\"preview\":{\"to_unames\":\"张三\",\"works\":\"完成接口联调\",\"plans\":\"明日回归测试\"}}"));
		OverAllState state = mockState(Map.of(), "确认", messages);

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
		AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
		AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
		JSONObject args = JSON.parseObject(toolCall.arguments());
		assertThat(args.getString("to_unames")).isEqualTo("张三");
		assertThat(args.getString("works")).isEqualTo("完成接口联调");
		assertThat(args.getString("plans")).isEqualTo("明日回归测试");
		assertThat(args.getBoolean("confirmed")).isTrue();
	}

	@Test
	void shouldResumeLeaveCapabilityWhenDraftIsActive() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{\"leave_type\":\"年假\",\"leave_days\":\"1\"}");
		Map<String, Object> stateData = new LinkedHashMap<>();
		stateData.put("capability_draft_submit_leave_status", "SLOT_MISSING");
		stateData.put("capability_draft_submit_leave", Map.of("leave_reason", "回家办事"));
		List<Message> messages = List.of(toolResponse(
				"call_1",
				"submit_leave",
				"{\"status\":\"SLOT_MISSING\",\"tool_name\":\"submit_leave\",\"missing_fields\":[\"leave_type\",\"leave_days\"],\"missing_field_labels\":{\"leave_type\":\"请假类型\",\"leave_days\":\"请假天数\"}}"));
		OverAllState state = mockState(stateData, "请假类型 年假，请假时长 1 天", messages);

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
		AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
		AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
		assertThat(toolCall.name()).isEqualTo("submit_leave");
		JSONObject args = JSON.parseObject(toolCall.arguments());
		assertThat(args.getString("leave_reason")).isEqualTo("回家办事");
		assertThat(args.getString("leave_type")).isEqualTo("年假");
		assertThat(args.getString("leave_days")).isEqualTo("1");
	}

	@Test
	void shouldResumeMeetingRoomCapabilityFromHistorySnapshot() {
		WorkReportDraftResumeReactHook hook = hookWithLlmOutput("{\"room_name\":\"3A\",\"start_time\":\"15:00\"}");
		List<Message> messages = List.of(toolResponse(
				"call_1",
				"reserve_meeting_room",
				"{\"status\":\"SLOT_MISSING\",\"tool_name\":\"reserve_meeting_room\",\"missing_fields\":[\"room_name\",\"start_time\"],\"message\":\"请补充会议室与开始时间\"}"));
		OverAllState state = mockState(Map.of(), "会议室 3A，开始时间 15:00", messages);

		Map<String, Object> updates = hook.beforeAgent(state, null).join();

		assertThat(updates.get("jump_to")).isEqualTo(JumpTo.tool);
		AssistantMessage assistantMessage = (AssistantMessage) ((List<?>) updates.get("messages")).get(0);
		AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
		assertThat(toolCall.name()).isEqualTo("reserve_meeting_room");
		JSONObject args = JSON.parseObject(toolCall.arguments());
		assertThat(args.getString("room_name")).isEqualTo("3A");
		assertThat(args.getString("start_time")).isEqualTo("15:00");
	}

	private WorkReportDraftResumeReactHook hookWithLlmOutput(String llmOutput) {
		ChatModel chatModel = mock(ChatModel.class, RETURNS_DEEP_STUBS);
		when(chatModel.call(any(Prompt.class)).getResult().getOutput().getText()).thenReturn(llmOutput);
		return new WorkReportDraftResumeReactHook(chatModel);
	}

	private OverAllState mockState(Map<String, Object> stateData, String input, List<Message> messages) {
		OverAllState state = mock(OverAllState.class);
		when(state.data()).thenReturn(new LinkedHashMap<>(stateData));
		when(state.value("input", String.class)).thenReturn(Optional.ofNullable(input));
		if (messages == null) {
			when(state.value("messages")).thenReturn(Optional.empty());
		}
		else {
			when(state.value("messages")).thenReturn(Optional.of(messages));
		}
		return state;
	}

	private ToolResponseMessage toolResponse(String id, String toolName, String responseData) {
		return ToolResponseMessage.builder().responses(List.of(
				new ToolResponseMessage.ToolResponse(id, toolName, responseData)
		)).build();
	}
}
