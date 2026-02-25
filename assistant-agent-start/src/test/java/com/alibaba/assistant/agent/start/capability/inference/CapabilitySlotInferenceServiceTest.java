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
package com.alibaba.assistant.agent.start.capability.inference;

import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilitySlotInferenceServiceTest {

	@Test
	void shouldMapOptionLabelToValueWhenLlmReturnsDisplayText() {
		ChatModel chatModel = mock(ChatModel.class, RETURNS_DEEP_STUBS);
		when(chatModel.call(any(Prompt.class)).getResult().getOutput().getText())
				.thenReturn("""
						```json
						{"types":"周报","send":"立即发送","unknown":"x"}
						```
						""");

		CapabilitySlotInferenceService service = new CapabilitySlotInferenceService(new ObjectMapper(), chatModel);
		CapabilityRegistrationProperties.HttpFormCapability capability = buildWorkReportCapability("LLM", "LLM");

		Map<String, Object> inferred = service.inferMissingSlots(
				capability,
				List.of("types", "send"),
				Map.of(),
				"我要写周报并立即发送",
				Map.of());

		assertThat(inferred).containsEntry("types", "2");
		assertThat(inferred).containsEntry("send", "1");
		assertThat(inferred).doesNotContainKey("unknown");
	}

	@Test
	void shouldSkipFieldsWhenInferModeDisabled() {
		ChatModel chatModel = mock(ChatModel.class, RETURNS_DEEP_STUBS);
		when(chatModel.call(any(Prompt.class)).getResult().getOutput().getText())
				.thenReturn("{\"types\":\"2\"}");

		CapabilitySlotInferenceService service = new CapabilitySlotInferenceService(new ObjectMapper(), chatModel);
		CapabilityRegistrationProperties.HttpFormCapability capability = buildWorkReportCapability("NONE", "LLM");

		Map<String, Object> inferred = service.inferMissingSlots(
				capability,
				List.of("types"),
				Map.of(),
				"我要写周报",
				Map.of());

		assertThat(inferred).isEmpty();
	}

	@Test
	void shouldReturnEmptyWhenChatModelUnavailable() {
		CapabilitySlotInferenceService service = new CapabilitySlotInferenceService(new ObjectMapper(), (ChatModel) null);
		CapabilityRegistrationProperties.HttpFormCapability capability = buildWorkReportCapability("LLM", "LLM");

		Map<String, Object> inferred = service.inferMissingSlots(
				capability,
				List.of("types"),
				Map.of(),
				"我要写周报",
				Map.of());

		assertThat(inferred).isEmpty();
	}

	private CapabilityRegistrationProperties.HttpFormCapability buildWorkReportCapability(String typesInferMode,
			String sendInferMode) {
		CapabilityRegistrationProperties.HttpFormCapability capability =
				new CapabilityRegistrationProperties.HttpFormCapability();
		capability.setToolName("submit_office_work_report");
		capability.setFields(List.of(
				fieldWithOptions("types", true, "汇报类型", typesInferMode, List.of(
						option("日报", "1"),
						option("周报", "2"),
						option("月报", "3"))),
				fieldWithOptions("send", true, "发送方式", sendInferMode, List.of(
						option("仅保存", "0"),
						option("立即发送", "1")))
		));
		return capability;
	}

	private CapabilityRegistrationProperties.FieldSpec fieldWithOptions(
			String name,
			boolean required,
			String description,
			String inferMode,
			List<CapabilityRegistrationProperties.FieldOption> options) {
		CapabilityRegistrationProperties.FieldSpec field = new CapabilityRegistrationProperties.FieldSpec();
		field.setName(name);
		field.setRequired(required);
		field.setDescription(description);
		field.setInputMode("SELECT_SINGLE");
		field.setInferMode(inferMode);
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
