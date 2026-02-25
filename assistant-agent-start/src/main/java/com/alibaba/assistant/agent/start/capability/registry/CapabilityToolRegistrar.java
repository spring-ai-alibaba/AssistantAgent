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
import com.alibaba.assistant.agent.start.capability.inference.CapabilitySlotInferenceService;
import com.alibaba.assistant.agent.start.capability.provider.CapabilityProviderOrchestrator;
import com.alibaba.assistant.agent.start.capability.tool.RegisteredHttpFormCodeactTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Registrar for configuration-driven capability tools.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class CapabilityToolRegistrar {

	private static final Logger logger = LoggerFactory.getLogger(CapabilityToolRegistrar.class);

	private final CapabilityRegistrationProperties registrationProperties;

	private final ObjectMapper objectMapper;

	private final CapabilityProviderOrchestrator providerOrchestrator;

	private final CapabilitySlotInferenceService slotInferenceService;

	public CapabilityToolRegistrar(CapabilityRegistrationProperties registrationProperties, ObjectMapper objectMapper,
			CapabilityProviderOrchestrator providerOrchestrator) {
		this(registrationProperties, objectMapper, providerOrchestrator, null);
	}

	@Autowired
	public CapabilityToolRegistrar(CapabilityRegistrationProperties registrationProperties, ObjectMapper objectMapper,
			CapabilityProviderOrchestrator providerOrchestrator,
			@Nullable CapabilitySlotInferenceService slotInferenceService) {
		this.registrationProperties = registrationProperties;
		this.objectMapper = objectMapper;
		this.providerOrchestrator = providerOrchestrator;
		this.slotInferenceService = slotInferenceService;
	}

	/**
	 * Build registered capability tools from configuration.
	 *
	 * @return tools for CodeAct phase
	 */
	public List<CodeactTool> createRegisteredTools() {
		List<CodeactTool> tools = new ArrayList<>();
		List<CapabilityRegistrationProperties.HttpFormCapability> registrations = registrationProperties.getRegistrations();

		if (registrations == null || registrations.isEmpty()) {
			logger.info("CapabilityToolRegistrar#createRegisteredTools - reason=no capability registration configured");
			return tools;
		}

		for (CapabilityRegistrationProperties.HttpFormCapability registration : registrations) {
			if (registration == null || !registration.isEnabled()) {
				continue;
			}

			if (!isValidRegistration(registration)) {
				continue;
			}

			try {
				RegisteredHttpFormCodeactTool tool = new RegisteredHttpFormCodeactTool(
						registration,
						objectMapper,
						providerOrchestrator,
						slotInferenceService);
				tools.add(tool);
				logger.info("CapabilityToolRegistrar#createRegisteredTools - reason=capability tool registered, toolName={}, endpoint={}",
						registration.getToolName(), registration.getEndpointUrl());
			}
			catch (Exception e) {
				logger.error("CapabilityToolRegistrar#createRegisteredTools - reason=failed to register capability tool, toolName={}, error={}",
						registration.getToolName(), e.getMessage(), e);
			}
		}

		logger.info("CapabilityToolRegistrar#createRegisteredTools - reason=capability registration finished, count={}", tools.size());
		return tools;
	}

	private boolean isValidRegistration(CapabilityRegistrationProperties.HttpFormCapability registration) {
		if (!StringUtils.hasText(registration.getToolName())) {
			logger.warn("CapabilityToolRegistrar#isValidRegistration - reason=skip registration because toolName is empty");
			return false;
		}
		boolean hasEndpoint = StringUtils.hasText(registration.getEndpointUrl());
		boolean hasProviderCode = StringUtils.hasText(registration.getProviderCode());
		if (!hasEndpoint && !hasProviderCode) {
			logger.warn(
					"CapabilityToolRegistrar#isValidRegistration - reason=skip registration because endpointUrl/providerCode are both empty, toolName={}",
					registration.getToolName());
			return false;
		}
		if (!StringUtils.hasText(registration.getDescription())) {
			registration.setDescription("Registered business capability tool");
		}
		return true;
	}

}
