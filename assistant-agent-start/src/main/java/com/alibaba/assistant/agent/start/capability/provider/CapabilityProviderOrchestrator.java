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
package com.alibaba.assistant.agent.start.capability.provider;

import com.alibaba.assistant.agent.common.tools.ToolContextHelper;
import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestration for provider options/default/submit calls.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class CapabilityProviderOrchestrator {

	private static final String DEFAULT_TENANT = "default";

	private final CapabilityProviderConfigRegistry providerConfigRegistry;

	private final CapabilityProviderTokenService tokenService;

	private final CapabilityProviderClient providerClient;

	public CapabilityProviderOrchestrator(CapabilityProviderConfigRegistry providerConfigRegistry,
			CapabilityProviderTokenService tokenService, CapabilityProviderClient providerClient) {
		this.providerConfigRegistry = providerConfigRegistry;
		this.tokenService = tokenService;
		this.providerClient = providerClient;
	}

	public FieldHints queryFieldHints(CapabilityRegistrationProperties.HttpFormCapability capability,
			List<String> missingFields, Map<String, Object> currentArgs, String tenantId, String userId) {
		if (capability == null || !StringUtils.hasText(capability.getProviderCode()) || missingFields == null
				|| missingFields.isEmpty()) {
			return new FieldHints(Map.of(), Map.of());
		}

		CapabilityProviderConfig providerConfig = providerConfigRegistry.getRequired(tenantId, capability.getProviderCode());
		String accessToken = tokenService.resolveAccessToken(tenantId, userId, capability.getProviderCode());
		Map<String, String> authHeaders = buildAuthHeaders(providerConfig, accessToken);
		Map<String, CapabilityRegistrationProperties.FieldSpec> specByName = indexFields(capability.getFields());
		Map<String, Object> patchedArgs = new LinkedHashMap<>();
		Map<String, FieldHint> hints = new LinkedHashMap<>();
		Map<String, Object> safeArgs = currentArgs != null ? new LinkedHashMap<>(currentArgs) : new LinkedHashMap<>();

		for (String fieldName : missingFields) {
			CapabilityRegistrationProperties.FieldSpec fieldSpec = specByName.get(fieldName);
			if (fieldSpec == null || !StringUtils.hasText(fieldSpec.getDefaultValueAction())) {
				continue;
			}
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("action", fieldSpec.getDefaultValueAction());
			payload.put("tool_name", capability.getToolName());
			payload.put("field_name", fieldName);
			payload.put("slots", safeArgs);
			payload.put("tenant_id", tenantId);
			payload.put("user_id", userId);
			Map<String, Object> response = providerClient.postJson(providerConfig, providerConfig.getDefaultValuePath(), payload,
					authHeaders);
			Map<String, Object> data = unwrapData(response);
			Object value = data.get("value");
			if (value == null || !StringUtils.hasText(String.valueOf(value))) {
				continue;
			}
			patchedArgs.put(fieldName, value);
			safeArgs.put(fieldName, value);
			String label = data.get("label") != null ? String.valueOf(data.get("label")) : String.valueOf(value);
			hints.put(fieldName, new FieldHint(
					fieldSpec.getInputMode(),
					List.of(),
					null,
					false,
					fieldSpec.getDependsOn(),
					new OptionItem(label, String.valueOf(value), Map.of()),
					true));
		}

		for (String fieldName : missingFields) {
			if (patchedArgs.containsKey(fieldName)) {
				continue;
			}
			CapabilityRegistrationProperties.FieldSpec fieldSpec = specByName.get(fieldName);
			if (fieldSpec == null || !StringUtils.hasText(fieldSpec.getOptionQueryAction())) {
				continue;
			}
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("action", fieldSpec.getOptionQueryAction());
			payload.put("tool_name", capability.getToolName());
			payload.put("field_name", fieldName);
			payload.put("slots", safeArgs);
			payload.put("tenant_id", tenantId);
			payload.put("user_id", userId);
			payload.put("cursor", extractCursor(safeArgs, fieldName));
			payload.put("limit", fieldSpec.getOptionPageSize());

			Map<String, Object> response = providerClient.postJson(providerConfig, providerConfig.getOptionsQueryPath(), payload,
					authHeaders);
			Map<String, Object> data = unwrapData(response);
			List<OptionItem> items = parseOptionItems(data.get("items"));
			String nextCursor = asText(data.get("cursor"));
			boolean hasMore = parseBoolean(data.get("has_more"));
			hints.put(fieldName, new FieldHint(
					fieldSpec.getInputMode(),
					items,
					nextCursor,
					hasMore,
					fieldSpec.getDependsOn(),
					null,
					false));
		}

		return new FieldHints(hints, patchedArgs);
	}

	public ProviderSubmitResult submit(CapabilityRegistrationProperties.HttpFormCapability capability,
			Map<String, String> formData, Map<String, Object> originalArgs, String tenantId, String userId) {
		if (capability == null || !StringUtils.hasText(capability.getProviderCode())) {
			return ProviderSubmitResult.notHandled();
		}

		CapabilityProviderConfig providerConfig = providerConfigRegistry.getRequired(tenantId, capability.getProviderCode());
		String accessToken = tokenService.resolveAccessToken(tenantId, userId, capability.getProviderCode());
		Map<String, String> authHeaders = buildAuthHeaders(providerConfig, accessToken);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("action", StringUtils.hasText(capability.getSubmitAction()) ? capability.getSubmitAction() : "submit");
		payload.put("tool_name", capability.getToolName());
		payload.put("form_data", formData != null ? formData : Map.of());
		payload.put("arguments", originalArgs != null ? originalArgs : Map.of());
		payload.put("tenant_id", tenantId);
		payload.put("user_id", userId);

		Map<String, Object> response = providerClient.postJson(providerConfig, providerConfig.getSubmitPath(), payload, authHeaders);
		Map<String, Object> data = unwrapData(response);
		return ProviderSubmitResult.handled(true, 200, response, data);
	}

	public InvocationContext resolveInvocationContext(@Nullable ToolContext toolContext) {
		String tenantId = ToolContextHelper.getFromMetadata(toolContext, "tenant_id")
				.or(() -> ToolContextHelper.getFromMetadata(toolContext, "tenant"))
				.orElse(DEFAULT_TENANT);
		String userId = ToolContextHelper.getFromMetadata(toolContext, "user_id")
				.or(() -> ToolContextHelper.getFromMetadata(toolContext, "userId"))
				.orElse(null);
		if (!StringUtils.hasText(userId)) {
			throw new CapabilityProviderException(
					CapabilityProviderErrorCode.INVOCATION_CONTEXT_MISSING,
					"user_id metadata missing for provider capability call");
		}
		return new InvocationContext(tenantId, userId);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> unwrapData(Map<String, Object> response) {
		if (response == null || response.isEmpty()) {
			return Map.of();
		}
		Object code = response.get("code");
		if (code != null) {
			String text = String.valueOf(code);
			if (!"OK".equalsIgnoreCase(text) && !"0".equals(text) && !"200".equals(text)) {
				throw new CapabilityProviderException(CapabilityProviderErrorCode.PROVIDER_CALL_FAILED,
						"provider returns non-success code=%s".formatted(text));
			}
		}
		Object data = response.get("data");
		if (!(data instanceof Map<?, ?> map)) {
			return response;
		}
		Map<String, Object> normalized = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey() != null) {
				normalized.put(String.valueOf(entry.getKey()), entry.getValue());
			}
		}
		return normalized;
	}

	@SuppressWarnings("unchecked")
	private List<OptionItem> parseOptionItems(Object items) {
		if (!(items instanceof List<?> rawItems) || rawItems.isEmpty()) {
			return List.of();
		}
		List<OptionItem> parsed = new ArrayList<>();
		for (Object item : rawItems) {
			if (!(item instanceof Map<?, ?> map)) {
				continue;
			}
			String label = asText(map.get("label"));
			String value = asText(map.get("value"));
			if (!StringUtils.hasText(value)) {
				continue;
			}
			Map<String, Object> extras = new LinkedHashMap<>();
			Object extraObj = map.get("extra");
			if (extraObj instanceof Map<?, ?> extraMap) {
				for (Map.Entry<?, ?> entry : extraMap.entrySet()) {
					if (entry.getKey() != null) {
						extras.put(String.valueOf(entry.getKey()), entry.getValue());
					}
				}
			}
			parsed.add(new OptionItem(StringUtils.hasText(label) ? label : value, value, extras));
		}
		return parsed;
	}

	private String extractCursor(Map<String, Object> args, String fieldName) {
		if (args == null || !StringUtils.hasText(fieldName)) {
			return null;
		}
		String[] keys = new String[] {
				fieldName + "_cursor",
				"cursor_" + fieldName
		};
		for (String key : keys) {
			Object value = args.get(key);
			if (value != null && StringUtils.hasText(String.valueOf(value))) {
				return String.valueOf(value);
			}
		}
		return null;
	}

	private boolean parseBoolean(Object value) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof Number number) {
			return number.intValue() > 0;
		}
		if (value != null) {
			String text = String.valueOf(value).trim().toLowerCase();
			return "true".equals(text) || "1".equals(text) || "yes".equals(text);
		}
		return false;
	}

	private String asText(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value);
		return StringUtils.hasText(text) ? text : null;
	}

	private Map<String, CapabilityRegistrationProperties.FieldSpec> indexFields(
			List<CapabilityRegistrationProperties.FieldSpec> fields) {
		if (fields == null || fields.isEmpty()) {
			return Map.of();
		}
		Map<String, CapabilityRegistrationProperties.FieldSpec> map = new LinkedHashMap<>();
		for (CapabilityRegistrationProperties.FieldSpec field : fields) {
			if (field != null && StringUtils.hasText(field.getName())) {
				map.put(field.getName(), field);
			}
		}
		return map;
	}

	private Map<String, String> buildAuthHeaders(CapabilityProviderConfig config, String token) {
		if (!StringUtils.hasText(token)) {
			return Map.of();
		}
		String headerName = StringUtils.hasText(config.getTokenHeaderName()) ? config.getTokenHeaderName() : "Authorization";
		String value = (config.getTokenPrefix() != null ? config.getTokenPrefix() : "") + token;
		return Map.of(headerName, value);
	}

	public record InvocationContext(String tenantId, String userId) {
	}

	public record OptionItem(String label, String value, Map<String, Object> extra) {
	}

	public record FieldHint(
			String inputMode,
			List<OptionItem> options,
			String nextCursor,
			boolean hasMore,
			List<String> dependsOn,
			OptionItem defaultValue,
			boolean defaultApplied) {
	}

	public record FieldHints(Map<String, FieldHint> hintsByField, Map<String, Object> patchedSlots) {
	}

	public record ProviderSubmitResult(boolean handled, boolean success, int httpStatus, Map<String, Object> response,
			Map<String, Object> data) {

		public static ProviderSubmitResult notHandled() {
			return new ProviderSubmitResult(false, false, 0, Map.of(), Map.of());
		}

		public static ProviderSubmitResult handled(boolean success, int httpStatus, Map<String, Object> response,
				Map<String, Object> data) {
			return new ProviderSubmitResult(true, success, httpStatus, response, data);
		}

	}

}

