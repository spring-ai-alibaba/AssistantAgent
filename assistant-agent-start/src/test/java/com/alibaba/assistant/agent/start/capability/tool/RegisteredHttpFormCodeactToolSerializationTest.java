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
package com.alibaba.assistant.agent.start.capability.tool;

import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import com.alibaba.assistant.agent.start.capability.inference.CapabilitySlotInferenceService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisteredHttpFormCodeactToolSerializationTest {

	@Test
	void shouldSerializeStateContainingRegisteredHttpFormCodeactTool() {
		CapabilityRegistrationProperties.HttpFormCapability capability =
				new CapabilityRegistrationProperties.HttpFormCapability();
		capability.setToolName("submit_office_work_report");
		capability.setDescription("Submit OA daily work report");
		capability.setEndpointUrl("http://office.test/oa/work/add");
		capability.setMethod("POST");

		RegisteredHttpFormCodeactTool tool = new RegisteredHttpFormCodeactTool(capability, new ObjectMapper());
		Map<String, Object> state = Map.of("codeact_tools", List.of(tool));

		ObjectMapper mapper = new ObjectMapper()
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
				.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		assertDoesNotThrow(() -> mapper.writeValueAsString(state));
	}

	@Test
	void shouldUseBusinessLabelsWhenMissingLeaveSlots() throws Exception {
		CapabilityRegistrationProperties.HttpFormCapability capability =
				new CapabilityRegistrationProperties.HttpFormCapability();
		capability.setToolName("submit_leave_application");
		capability.setDescription("Submit OA leave application");
		capability.setSlotFillingEnabled(true);
		capability.setConfirmationRequired(true);
		capability.setFields(List.of(
				field("types", true, "请假类型"),
				field("start_date", true, "开始日期"),
				field("reason", true, "请假原因")
		));

		RegisteredHttpFormCodeactTool tool = new RegisteredHttpFormCodeactTool(capability, new ObjectMapper());
		String response = tool.call("{}");

		JsonNode root = new ObjectMapper().readTree(response);
		assertEquals("SLOT_MISSING", root.path("status").asText());
		assertEquals("请假类型", root.path("missing_field_labels").path("types").asText());
		assertEquals("开始日期", root.path("missing_field_labels").path("start_date").asText());
		assertTrue(root.path("message").asText().contains("请假类型"));
	}

	@Test
	void shouldUseBusinessLabelsWhenMissingMeetingSlots() throws Exception {
		CapabilityRegistrationProperties.HttpFormCapability capability =
				new CapabilityRegistrationProperties.HttpFormCapability();
		capability.setToolName("submit_meeting_room_booking");
		capability.setDescription("Submit OA meeting room booking");
		capability.setSlotFillingEnabled(true);
		capability.setFields(List.of(
				field("room_id", true, "会议室"),
				field("title", true, "会议主题"),
				field("num", true, "参会人数")
		));

		RegisteredHttpFormCodeactTool tool = new RegisteredHttpFormCodeactTool(capability, new ObjectMapper());
		String response = tool.call("{}");

		JsonNode root = new ObjectMapper().readTree(response);
		assertEquals("SLOT_MISSING", root.path("status").asText());
		assertEquals("会议室", root.path("missing_field_labels").path("room_id").asText());
		assertEquals("会议主题", root.path("missing_field_labels").path("title").asText());
		assertEquals("参会人数", root.path("missing_field_labels").path("num").asText());
	}

	@Test
	void shouldReturnQuestionPlanFromFieldDefinition() throws Exception {
		CapabilityRegistrationProperties.HttpFormCapability capability =
				new CapabilityRegistrationProperties.HttpFormCapability();
		capability.setToolName("submit_office_work_report");
		capability.setDescription("Submit OA daily work report");
		capability.setSlotFillingEnabled(true);
		capability.setConfirmationRequired(true);
		capability.setFields(List.of(
				fieldWithOptions("types", true, "汇报类型", "SINGLE", List.of(
						option("日报", "1"),
						option("周报", "2"),
						option("月报", "3"))),
				field("works", false, "本期工作内容")
		));

		RegisteredHttpFormCodeactTool tool = new RegisteredHttpFormCodeactTool(capability, new ObjectMapper());
		String response = tool.call("{}");

		JsonNode root = new ObjectMapper().readTree(response);
		assertEquals("SLOT_MISSING", root.path("status").asText());
		assertEquals("COLLECT", root.path("question_plan").path("step").asText());
		assertEquals("types", root.path("question_plan").path("next_field").path("name").asText());
		JsonNode firstField = root.path("question_plan").path("fields").get(0);
		assertEquals("SINGLE", firstField.path("ask_mode").asText());
		assertEquals("SELECT_SINGLE", firstField.path("input_mode").asText());
		assertEquals("日报", firstField.path("options").get(0).path("label").asText());
		assertEquals("1", firstField.path("options").get(0).path("value").asText());
		assertTrue(root.path("message").asText().contains("可选"));
	}

	@Test
	void shouldReturnConfirmQuestionPlanWhenAllRequiredSlotsCollected() throws Exception {
		CapabilityRegistrationProperties.HttpFormCapability capability =
				new CapabilityRegistrationProperties.HttpFormCapability();
		capability.setToolName("submit_office_work_report");
		capability.setDescription("Submit OA daily work report");
		capability.setSlotFillingEnabled(true);
		capability.setConfirmationRequired(true);
		capability.setFields(List.of(
				fieldWithOptions("types", true, "汇报类型", "SINGLE", List.of(
						option("日报", "1"),
						option("周报", "2"),
						option("月报", "3"))),
				field("works", true, "本期工作内容")
		));

		RegisteredHttpFormCodeactTool tool = new RegisteredHttpFormCodeactTool(capability, new ObjectMapper());
		String response = tool.call("{\"types\":\"2\",\"works\":\"本周完成联调\"}");

		JsonNode root = new ObjectMapper().readTree(response);
		assertEquals("WAIT_CONFIRM", root.path("status").asText());
		assertEquals("CONFIRM", root.path("question_plan").path("step").asText());
		assertEquals("confirmed", root.path("question_plan").path("confirmation_arg_name").asText());
		assertEquals("2", root.path("question_plan").path("preview").path("types").asText());
	}

	@Test
	void shouldApplyInferredSlotsBeforeReturningWaitConfirm() throws Exception {
		CapabilityRegistrationProperties.HttpFormCapability capability =
				new CapabilityRegistrationProperties.HttpFormCapability();
		capability.setToolName("submit_office_work_report");
		capability.setDescription("Submit OA daily work report");
		capability.setSlotFillingEnabled(true);
		capability.setConfirmationRequired(true);
		capability.setFields(List.of(
				fieldWithOptions("types", true, "汇报类型", "SINGLE", List.of(
						option("日报", "1"),
						option("周报", "2"),
						option("月报", "3"))),
				field("works", true, "本期工作内容")
		));

		CapabilitySlotInferenceService inferenceService = mock(CapabilitySlotInferenceService.class);
		when(inferenceService.inferMissingSlots(any(), anyList(), anyMap(), any(), anyMap()))
				.thenReturn(Map.of("types", "2"));

		RegisteredHttpFormCodeactTool tool = new RegisteredHttpFormCodeactTool(
				capability,
				new ObjectMapper(),
				null,
				inferenceService);
		String response = tool.call("{\"works\":\"本周完成联调\"}");

		JsonNode root = new ObjectMapper().readTree(response);
		assertEquals("WAIT_CONFIRM", root.path("status").asText());
		assertEquals("2", root.path("preview").path("types").asText());
		assertEquals("2", root.path("inferred_slots").path("types").asText());
	}

	private CapabilityRegistrationProperties.FieldSpec field(String name, boolean required, String description) {
		CapabilityRegistrationProperties.FieldSpec field = new CapabilityRegistrationProperties.FieldSpec();
		field.setName(name);
		field.setRequired(required);
		field.setDescription(description);
		return field;
	}

	private CapabilityRegistrationProperties.FieldSpec fieldWithOptions(
			String name,
			boolean required,
			String description,
			String askMode,
			List<CapabilityRegistrationProperties.FieldOption> options) {
		CapabilityRegistrationProperties.FieldSpec field = field(name, required, description);
		field.setInputMode("SELECT_SINGLE");
		field.setAskMode(askMode);
		field.setOptions(options);
		return field;
	}

	private CapabilityRegistrationProperties.FieldOption option(String label, String value) {
		CapabilityRegistrationProperties.FieldOption option = new CapabilityRegistrationProperties.FieldOption();
		option.setLabel(label);
		option.setValue(value);
		return option;
	}
}
