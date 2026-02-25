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
package com.alibaba.assistant.agent.start.capability.registry;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityToolRegistrarTest {

	@Test
	void shouldRegisterCapabilityWhenProviderCodeConfiguredWithoutEndpointUrl() {
		CapabilityRegistrationProperties properties = new CapabilityRegistrationProperties();
		CapabilityRegistrationProperties.HttpFormCapability capability =
				new CapabilityRegistrationProperties.HttpFormCapability();
		capability.setEnabled(true);
		capability.setToolName("submit_work_report");
		capability.setDescription("Submit work report by provider");
		capability.setProviderCode("oa-capability-provider");
		properties.setRegistrations(List.of(capability));

		CapabilityToolRegistrar registrar = new CapabilityToolRegistrar(properties, new ObjectMapper(), null);
		List<CodeactTool> tools = registrar.createRegisteredTools();

		assertThat(tools).hasSize(1);
		assertThat(tools.get(0).getToolDefinition().name()).isEqualTo("submit_work_report");
	}

}

