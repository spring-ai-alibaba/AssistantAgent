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

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.extension.dynamic.tool.AbstractDynamicCodeactTool;
import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import com.alibaba.assistant.agent.start.capability.inference.CapabilitySlotInferenceService;
import com.alibaba.assistant.agent.start.capability.provider.CapabilityProviderException;
import com.alibaba.assistant.agent.start.capability.provider.CapabilityProviderOrchestrator;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Generic HTTP form capability tool created from capability registration.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class RegisteredHttpFormCodeactTool extends AbstractDynamicCodeactTool {

	private static final Logger logger = LoggerFactory.getLogger(RegisteredHttpFormCodeactTool.class);

	private static final String STATUS_SLOT_MISSING = "SLOT_MISSING";

	private static final String STATUS_WAIT_CONFIRM = "WAIT_CONFIRM";

	private static final String STATUS_SUBMITTED = "SUBMITTED";

	private static final String STATUS_SUBMIT_FAILED = "SUBMIT_FAILED";

	private static final Map<String, String> DEFAULT_FIELD_LABELS = createDefaultFieldLabels();

	private final CapabilityRegistrationProperties.HttpFormCapability registration;

	private final URI endpointUri;

	private final String method;

	private final String contentType;

	private final Map<String, String> staticHeaders;

	private final Map<String, String> headerArgs;

	private final Map<String, String> defaultFormData;

	private final List<String> formFieldNames;

	private final long connectTimeoutMs;

	private final long readTimeoutMs;

	private final boolean slotFillingEnabled;

	private final boolean confirmationRequired;

	private final String confirmationArgName;

	private final String draftStateKey;

	private final String draftStatusStateKey;

	private final List<String> requiredInputFields;

	private final Map<String, CapabilityRegistrationProperties.FieldSpec> fieldSpecByName;

	private final String providerCode;

	@JsonIgnore
	private final CapabilityProviderOrchestrator providerOrchestrator;

	@JsonIgnore
	private final CapabilitySlotInferenceService slotInferenceService;

	public RegisteredHttpFormCodeactTool(CapabilityRegistrationProperties.HttpFormCapability registration,
			ObjectMapper objectMapper) {
		this(registration, objectMapper, null, null);
	}

	public RegisteredHttpFormCodeactTool(CapabilityRegistrationProperties.HttpFormCapability registration,
			ObjectMapper objectMapper, @Nullable CapabilityProviderOrchestrator providerOrchestrator) {
		this(registration, objectMapper, providerOrchestrator, null);
	}

	public RegisteredHttpFormCodeactTool(CapabilityRegistrationProperties.HttpFormCapability registration,
			ObjectMapper objectMapper, @Nullable CapabilityProviderOrchestrator providerOrchestrator,
			@Nullable CapabilitySlotInferenceService slotInferenceService) {
		super(objectMapper, buildToolDefinition(registration, objectMapper), buildMetadata(registration));
		this.registration = registration;
		this.providerOrchestrator = providerOrchestrator;
		this.slotInferenceService = slotInferenceService;
		this.providerCode = registration.getProviderCode();
		this.endpointUri = StringUtils.hasText(registration.getEndpointUrl()) ? URI.create(registration.getEndpointUrl()) : null;
		this.method = normalizeMethod(registration.getMethod());
		this.contentType = StringUtils.hasText(registration.getContentType())
				? registration.getContentType()
				: "application/x-www-form-urlencoded; charset=UTF-8";
		this.staticHeaders = new LinkedHashMap<>(registration.getHeaders());
		this.headerArgs = new LinkedHashMap<>(registration.getHeaderArgs());
		this.defaultFormData = new LinkedHashMap<>(registration.getDefaultFormData());
		this.formFieldNames = new ArrayList<>(registration.getFormFieldNames());
		this.connectTimeoutMs = registration.getConnectTimeoutMs() > 0 ? registration.getConnectTimeoutMs() : 5000;
		this.readTimeoutMs = registration.getReadTimeoutMs() > 0 ? registration.getReadTimeoutMs() : 10000;
		this.slotFillingEnabled = registration.isSlotFillingEnabled();
		this.confirmationRequired = registration.isConfirmationRequired();
		this.confirmationArgName = StringUtils.hasText(registration.getConfirmationArgName())
				? registration.getConfirmationArgName().trim()
				: "confirmed";
		this.draftStateKey = "capability_draft_" + registration.getToolName();
		this.draftStatusStateKey = draftStateKey + "_status";
		this.requiredInputFields = new ArrayList<>();
		this.fieldSpecByName = new LinkedHashMap<>();
		for (CapabilityRegistrationProperties.FieldSpec field : registration.getFields()) {
			if (field == null || !StringUtils.hasText(field.getName())) {
				continue;
			}
			fieldSpecByName.put(field.getName(), field);
			if (field.isRequired() && !requiredInputFields.contains(field.getName())) {
				requiredInputFields.add(field.getName());
			}
		}
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, @Nullable ToolContext toolContext) {
		try {
			Map<String, Object> currentArgs = parseInput(toolInput);

			if (!slotFillingEnabled && !confirmationRequired) {
				SubmissionResult submitResult = submit(currentArgs, toolContext);
				if (submitResult.success) {
					return submitResult.body;
				}
				return buildSubmitResponse(STATUS_SUBMIT_FAILED, submitResult);
			}

			Map<String, Object> mergedArgs = mergeDraftAndCurrent(loadDraft(toolContext), currentArgs);
			List<String> missingFields = collectMissingRequiredFields(mergedArgs);
			Map<String, CapabilityProviderOrchestrator.FieldHint> providerFieldHints = new LinkedHashMap<>();
			Map<String, Object> inferredSlots = new LinkedHashMap<>();

			if (!missingFields.isEmpty()) {
				missingFields = resolveMissingFieldsByProviderAndLlm(
						missingFields,
						mergedArgs,
						toolContext,
						providerFieldHints,
						inferredSlots);
			}

			if (!missingFields.isEmpty()) {
				if (slotFillingEnabled) {
					saveDraft(toolContext, mergedArgs, STATUS_SLOT_MISSING);
				}
				return buildSlotMissingResponse(missingFields, mergedArgs, providerFieldHints, inferredSlots);
			}

			if (confirmationRequired && !isConfirmed(currentArgs)) {
				saveDraft(toolContext, mergedArgs, STATUS_WAIT_CONFIRM);
				return buildWaitConfirmResponse(mergedArgs, inferredSlots);
			}

			SubmissionResult submitResult = submit(mergedArgs, toolContext);
			if (submitResult.success) {
				clearDraft(toolContext);
				return buildSubmitResponse(STATUS_SUBMITTED, submitResult);
			}
			saveDraft(toolContext, mergedArgs, STATUS_SUBMIT_FAILED);
			return buildSubmitResponse(STATUS_SUBMIT_FAILED, submitResult);
		}
		catch (CapabilityProviderException e) {
			logger.warn("RegisteredHttpFormCodeactTool#call - reason=provider capability failed, toolName={}, errorCode={}, error={}",
					toolDefinition.name(), e.getErrorCode(), e.getMessage());
			return buildProviderFailureResponse(e);
		}
		catch (Exception e) {
			logger.error("RegisteredHttpFormCodeactTool#call - reason=capability execution failed, toolName={}, error={}",
					toolDefinition.name(), e.getMessage(), e);
			return buildErrorResponse(e.getMessage());
		}
	}

	@Override
	protected String doCall(Map<String, Object> args, @Nullable ToolContext toolContext) throws Exception {
		SubmissionResult result = submit(args, toolContext);
		return result.success ? result.body : buildErrorResponse("http_status=" + result.httpStatus + ", body=" + result.body);
	}

	private SubmissionResult submit(Map<String, Object> args, @Nullable ToolContext toolContext) throws Exception {
		Map<String, Object> safeArgs = args != null ? args : Map.of();
		Map<String, String> requestHeaders = buildRequestHeaders(safeArgs);
		Map<String, String> formData = buildFormData(safeArgs);

		if (StringUtils.hasText(providerCode) && providerOrchestrator != null) {
			CapabilityProviderOrchestrator.InvocationContext context = providerOrchestrator.resolveInvocationContext(toolContext);
			CapabilityProviderOrchestrator.ProviderSubmitResult providerResult = providerOrchestrator.submit(
					registration,
					formData,
					safeArgs,
					context.tenantId(),
					context.userId());
			if (providerResult.handled()) {
				return new SubmissionResult(providerResult.success(), providerResult.httpStatus(), toJson(providerResult.response()),
						null);
			}
		}

		HttpRequest request = buildRequest(formData, requestHeaders);
		long start = System.currentTimeMillis();
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(connectTimeoutMs))
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		long costMs = System.currentTimeMillis() - start;
		logger.info("RegisteredHttpFormCodeactTool#submit - reason=capability request finished, toolName={}, status={}, costMs={}",
				toolDefinition.name(), response.statusCode(), costMs);

		return new SubmissionResult(
				response.statusCode() >= 200 && response.statusCode() < 300,
				response.statusCode(),
				response.body(),
				null
		);
	}

	private Map<String, String> buildRequestHeaders(Map<String, Object> args) {
		Map<String, String> requestHeaders = new LinkedHashMap<>(staticHeaders);
		for (Map.Entry<String, String> entry : headerArgs.entrySet()) {
			String headerName = entry.getKey();
			String argName = entry.getValue();
			Object argValue = args.get(argName);
			if (!isEmptyValue(argValue)) {
				requestHeaders.put(headerName, stringifyValue(argValue));
			}
		}
		return requestHeaders;
	}

	private Map<String, String> buildFormData(Map<String, Object> args) {
		Map<String, String> formData = new LinkedHashMap<>(defaultFormData);

		if (!formFieldNames.isEmpty()) {
			for (String fieldName : formFieldNames) {
				if (isControlArgument(fieldName)) {
					continue;
				}
				Object value = args.get(fieldName);
				if (!isEmptyValue(value)) {
					formData.put(fieldName, stringifyValue(value));
				}
			}
			return formData;
		}

		Set<String> headerArgNames = new HashSet<>(headerArgs.values());
		for (Map.Entry<String, Object> entry : args.entrySet()) {
			if (headerArgNames.contains(entry.getKey())) {
				continue;
			}
			if (isControlArgument(entry.getKey())) {
				continue;
			}
			if (!isEmptyValue(entry.getValue())) {
				formData.put(entry.getKey(), stringifyValue(entry.getValue()));
			}
		}
		return formData;
	}

	private HttpRequest buildRequest(Map<String, String> formData, Map<String, String> requestHeaders) {
		if (endpointUri == null) {
			throw new IllegalStateException("endpointUrl is empty and providerCode routing is not available");
		}
		URI requestUri = endpointUri;
		String encoded = toFormUrlEncoded(formData);

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.timeout(Duration.ofMillis(readTimeoutMs));

		if ("GET".equals(method)) {
			requestUri = appendQuery(endpointUri, encoded);
			builder.uri(requestUri).GET();
		}
		else {
			builder.uri(requestUri);
			builder.method(method, HttpRequest.BodyPublishers.ofString(encoded, StandardCharsets.UTF_8));
		}

		if (!requestHeaders.containsKey("Content-Type")) {
			builder.header("Content-Type", contentType);
		}
		for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}
		return builder.build();
	}

	private URI appendQuery(URI baseUri, String encodedQuery) {
		if (!StringUtils.hasText(encodedQuery)) {
			return baseUri;
		}
		String separator = baseUri.toString().contains("?") ? "&" : "?";
		return URI.create(baseUri + separator + encodedQuery);
	}

	private String toFormUrlEncoded(Map<String, String> data) {
		StringJoiner joiner = new StringJoiner("&");
		for (Map.Entry<String, String> entry : data.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
			String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
			joiner.add(key + "=" + value);
		}
		return joiner.toString();
	}

	private static String normalizeMethod(String method) {
		return StringUtils.hasText(method) ? method.trim().toUpperCase() : "POST";
	}

	private static ToolDefinition buildToolDefinition(CapabilityRegistrationProperties.HttpFormCapability registration,
			ObjectMapper objectMapper) {
		String inputSchema = buildInputSchema(registration, objectMapper);
		return DefaultToolDefinition.builder()
				.name(registration.getToolName())
				.description(registration.getDescription())
				.inputSchema(inputSchema)
				.build();
	}

	private static String buildInputSchema(CapabilityRegistrationProperties.HttpFormCapability registration,
			ObjectMapper objectMapper) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");

		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		for (CapabilityRegistrationProperties.FieldSpec field : registration.getFields()) {
			if (!StringUtils.hasText(field.getName())) {
				continue;
			}

			Map<String, Object> fieldSchema = new LinkedHashMap<>();
			fieldSchema.put("type", StringUtils.hasText(field.getType()) ? field.getType() : "string");
			if (StringUtils.hasText(field.getDescription())) {
				fieldSchema.put("description", field.getDescription());
			}
			if (field.getOptions() != null && !field.getOptions().isEmpty()) {
				List<String> enumValues = new ArrayList<>();
				List<String> enumLabels = new ArrayList<>();
				for (CapabilityRegistrationProperties.FieldOption option : field.getOptions()) {
					if (option == null || !StringUtils.hasText(option.getValue())) {
						continue;
					}
					enumValues.add(option.getValue());
					enumLabels.add(StringUtils.hasText(option.getLabel()) ? option.getLabel() : option.getValue());
				}
				if (!enumValues.isEmpty()) {
					fieldSchema.put("enum", enumValues);
					fieldSchema.put("x-enum-labels", enumLabels);
				}
			}
			if (field.getDefaultValue() != null) {
				fieldSchema.put("default", field.getDefaultValue());
			}
			properties.put(field.getName(), fieldSchema);

			if (field.isRequired()) {
				required.add(field.getName());
			}
		}

		if (registration.isConfirmationRequired()) {
			String confirmationArgName = StringUtils.hasText(registration.getConfirmationArgName())
					? registration.getConfirmationArgName().trim()
					: "confirmed";
			if (!properties.containsKey(confirmationArgName)) {
				Map<String, Object> confirmSchema = new LinkedHashMap<>();
				confirmSchema.put("type", "boolean");
				confirmSchema.put("description", "Set to true only when user explicitly confirms submission");
				properties.put(confirmationArgName, confirmSchema);
			}
		}

		schema.put("properties", properties);
		if (!required.isEmpty()) {
			schema.put("required", required);
		}

		try {
			return objectMapper.writeValueAsString(schema);
		}
		catch (JsonProcessingException e) {
			return "{}";
		}
	}

	private static CodeactToolMetadata buildMetadata(CapabilityRegistrationProperties.HttpFormCapability registration) {
		return DefaultCodeactToolMetadata.builder()
				.addSupportedLanguage(Language.PYTHON)
				.targetClassName(registration.getTargetClassName())
				.targetClassDescription(registration.getTargetClassDescription())
				// Business form tools should return directly to avoid model/tool empty-loop retries.
				.returnDirect(true)
				.build();
	}

	private List<String> collectMissingRequiredFields(Map<String, Object> mergedArgs) {
		if (requiredInputFields.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> missing = new ArrayList<>();
		for (String requiredField : requiredInputFields) {
			Object value = mergedArgs.get(requiredField);
			if (isEmptyValue(value) && isEmptyValue(defaultFormData.get(requiredField))) {
				missing.add(requiredField);
			}
		}
		return missing;
	}

	private Map<String, Object> mergeDraftAndCurrent(Map<String, Object> draft, Map<String, Object> currentArgs) {
		Map<String, Object> merged = new LinkedHashMap<>();
		if (draft != null) {
			for (Map.Entry<String, Object> entry : draft.entrySet()) {
				if (!isControlArgument(entry.getKey()) && !isEmptyValue(entry.getValue())) {
					merged.put(entry.getKey(), entry.getValue());
				}
			}
		}
		if (currentArgs != null) {
			for (Map.Entry<String, Object> entry : currentArgs.entrySet()) {
				if (!isControlArgument(entry.getKey()) && !isEmptyValue(entry.getValue())) {
					merged.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return merged;
	}

	private boolean isControlArgument(String argumentName) {
		return confirmationRequired && confirmationArgName.equals(argumentName);
	}

	private boolean isConfirmed(Map<String, Object> currentArgs) {
		Object value = currentArgs.get(confirmationArgName);
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof Number) {
			return ((Number) value).intValue() == 1;
		}
		if (value instanceof String) {
			String normalized = ((String) value).trim().toLowerCase();
			return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)
					|| "y".equals(normalized) || "confirm".equals(normalized) || "confirmed".equals(normalized)
					|| "确认".equals(normalized) || "执行".equals(normalized);
		}
		return false;
	}

	private boolean isEmptyValue(Object value) {
		if (value == null) {
			return true;
		}
		if (value instanceof String) {
			return !StringUtils.hasText((String) value);
		}
		if (value instanceof List<?> list) {
			return list.isEmpty();
		}
		if (value instanceof Map<?, ?> map) {
			return map.isEmpty();
		}
		return false;
	}

	private String stringifyValue(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof List<?> list) {
			return list.stream()
					.filter(Objects::nonNull)
					.map(String::valueOf)
					.collect(Collectors.joining(","));
		}
		return String.valueOf(value);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> loadDraft(@Nullable ToolContext toolContext) {
		Map<String, Object> draft = new LinkedHashMap<>();
		OverAllState state = getOverAllState(toolContext);
		if (state == null) {
			return draft;
		}

		Optional<Map> value = state.value(draftStateKey, Map.class);
		if (value.isPresent() && value.get() != null) {
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value.get()).entrySet()) {
				if (entry.getKey() != null) {
					draft.put(String.valueOf(entry.getKey()), entry.getValue());
				}
			}
		}
		return draft;
	}

	private void saveDraft(@Nullable ToolContext toolContext, Map<String, Object> draft, String status) {
		OverAllState state = getOverAllState(toolContext);
		if (state == null) {
			return;
		}
		Map<String, Object> updates = new LinkedHashMap<>();
		updates.put(draftStateKey, draft != null ? draft : new LinkedHashMap<>());
		updates.put(draftStatusStateKey, status);
		state.updateState(updates);
	}

	private void clearDraft(@Nullable ToolContext toolContext) {
		OverAllState state = getOverAllState(toolContext);
		if (state == null) {
			return;
		}
		Map<String, Object> updates = new LinkedHashMap<>();
		updates.put(draftStateKey, new LinkedHashMap<>());
		updates.put(draftStatusStateKey, "IDLE");
		state.updateState(updates);
	}

	private OverAllState getOverAllState(@Nullable ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			return null;
		}
		Object stateObj = toolContext.getContext().get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);
		if (stateObj instanceof OverAllState) {
			return (OverAllState) stateObj;
		}
		return null;
	}

	private CapabilityProviderOrchestrator.FieldHints queryProviderFieldHints(List<String> missingFields,
			Map<String, Object> mergedArgs, @Nullable ToolContext toolContext) {
		if (!StringUtils.hasText(providerCode) || providerOrchestrator == null || missingFields == null
				|| missingFields.isEmpty()) {
			return new CapabilityProviderOrchestrator.FieldHints(Map.of(), Map.of());
		}
		CapabilityProviderOrchestrator.InvocationContext context = providerOrchestrator.resolveInvocationContext(toolContext);
		return providerOrchestrator.queryFieldHints(
				registration,
				missingFields,
				mergedArgs != null ? mergedArgs : Map.of(),
				context.tenantId(),
				context.userId());
	}

	private List<String> resolveMissingFieldsByProviderAndLlm(
			List<String> missingFields,
			Map<String, Object> mergedArgs,
			@Nullable ToolContext toolContext,
			Map<String, CapabilityProviderOrchestrator.FieldHint> providerFieldHints,
			Map<String, Object> inferredSlots) {
		List<String> unresolved = missingFields != null ? new ArrayList<>(missingFields) : new ArrayList<>();
		if (unresolved.isEmpty()) {
			return unresolved;
		}

		CapabilityProviderOrchestrator.FieldHints providerHints = queryProviderFieldHints(unresolved, mergedArgs, toolContext);
		applyProviderFieldHints(providerHints, mergedArgs, providerFieldHints);
		unresolved = collectMissingRequiredFields(mergedArgs);
		if (unresolved.isEmpty()) {
			return unresolved;
		}

		Map<String, Object> inferred = inferMissingFieldsByLlm(unresolved, mergedArgs, toolContext, providerFieldHints);
		if (!inferred.isEmpty()) {
			mergedArgs.putAll(inferred);
			inferredSlots.putAll(inferred);
			unresolved = collectMissingRequiredFields(mergedArgs);
		}

		if (!unresolved.isEmpty() && !inferred.isEmpty()) {
			CapabilityProviderOrchestrator.FieldHints secondPassHints = queryProviderFieldHints(unresolved, mergedArgs, toolContext);
			applyProviderFieldHints(secondPassHints, mergedArgs, providerFieldHints);
			unresolved = collectMissingRequiredFields(mergedArgs);
		}
		return unresolved;
	}

	private void applyProviderFieldHints(@Nullable CapabilityProviderOrchestrator.FieldHints fieldHints,
			Map<String, Object> mergedArgs,
			Map<String, CapabilityProviderOrchestrator.FieldHint> providerFieldHints) {
		if (fieldHints == null) {
			return;
		}
		if (fieldHints.patchedSlots() != null && !fieldHints.patchedSlots().isEmpty()) {
			mergedArgs.putAll(fieldHints.patchedSlots());
		}
		if (fieldHints.hintsByField() != null && !fieldHints.hintsByField().isEmpty()) {
			providerFieldHints.putAll(fieldHints.hintsByField());
		}
	}

	private Map<String, Object> inferMissingFieldsByLlm(List<String> missingFields, Map<String, Object> mergedArgs,
			@Nullable ToolContext toolContext, Map<String, CapabilityProviderOrchestrator.FieldHint> providerFieldHints) {
		if (slotInferenceService == null || missingFields == null || missingFields.isEmpty()) {
			return Map.of();
		}
		String userInput = extractLatestUserInput(toolContext);
		Map<String, Object> inferred = slotInferenceService.inferMissingSlots(
				registration,
				missingFields,
				mergedArgs != null ? mergedArgs : Map.of(),
				userInput,
				providerFieldHints);
		if (inferred == null || inferred.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> sanitized = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : inferred.entrySet()) {
			if (!StringUtils.hasText(entry.getKey()) || isControlArgument(entry.getKey()) || isEmptyValue(entry.getValue())) {
				continue;
			}
			sanitized.put(entry.getKey(), entry.getValue());
		}
		return sanitized;
	}

	@SuppressWarnings("unchecked")
	private String extractLatestUserInput(@Nullable ToolContext toolContext) {
		OverAllState state = getOverAllState(toolContext);
		if (state == null) {
			return null;
		}
		String input = state.value("input", String.class).orElse(null);
		if (StringUtils.hasText(input)) {
			return input.trim();
		}

		Optional<Object> messagesOpt = state.value("messages");
		if (messagesOpt == null || messagesOpt.isEmpty() || !(messagesOpt.get() instanceof List<?> rawMessages)) {
			return null;
		}
		for (int i = rawMessages.size() - 1; i >= 0; i--) {
			Object msg = rawMessages.get(i);
			if (msg instanceof UserMessage userMessage) {
				return userMessage.getText();
			}
			if (msg instanceof Message message
					&& "USER".equalsIgnoreCase(String.valueOf(message.getMessageType()))
					&& StringUtils.hasText(message.getText())) {
				return message.getText();
			}
		}
		return null;
	}

	private String buildSlotMissingResponse(List<String> missingFields, Map<String, Object> mergedArgs,
			Map<String, CapabilityProviderOrchestrator.FieldHint> fieldHints, Map<String, Object> inferredSlots) {
		Map<String, Object> questionPlan = buildCollectQuestionPlan(missingFields, mergedArgs, fieldHints);
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("status", STATUS_SLOT_MISSING);
		response.put("tool_name", toolDefinition.name());
		response.put("missing_fields", missingFields);
		response.put("missing_field_labels", buildFieldLabelMap(missingFields));
		response.put("missing_field_descriptions", buildFieldDescriptionMap(missingFields));
		response.put("collected_slots", mergedArgs);
		if (inferredSlots != null && !inferredSlots.isEmpty()) {
			response.put("inferred_slots", inferredSlots);
		}
		if (fieldHints != null && !fieldHints.isEmpty()) {
			response.put("field_hints", toJsonFieldHints(fieldHints));
		}
		response.put("question_plan", questionPlan);
		response.put("message", buildMissingFieldPrompt(missingFields, mergedArgs, fieldHints));
		return toJson(response);
	}

	private String buildWaitConfirmResponse(Map<String, Object> mergedArgs, Map<String, Object> inferredSlots) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("status", STATUS_WAIT_CONFIRM);
		response.put("tool_name", toolDefinition.name());
		response.put("confirmation_required", true);
		response.put("confirmation_arg_name", confirmationArgName);
		response.put("preview", mergedArgs);
		if (inferredSlots != null && !inferredSlots.isEmpty()) {
			response.put("inferred_slots", inferredSlots);
		}
		response.put("question_plan", buildConfirmQuestionPlan(mergedArgs));
		response.put("message", "信息已收集完整。请确认无误后回复“确认提交”继续。");
		return toJson(response);
	}

	private String buildSubmitResponse(String status, SubmissionResult result) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("status", status);
		response.put("tool_name", toolDefinition.name());
		response.put("http_status", result.httpStatus);
		response.put("success", result.success);
		response.put("response", tryParseJson(result.body));
		if (!result.success) {
			if (StringUtils.hasText(result.errorCode)) {
				response.put("error_code", result.errorCode);
			}
			response.put("message", buildSubmitFailMessage(result.errorCode));
		}
		return toJson(response);
	}

	private String buildProviderFailureResponse(CapabilityProviderException exception) {
		SubmissionResult submitResult = new SubmissionResult(
				false,
				400,
				exception.getMessage(),
				exception.getErrorCode());
		return buildSubmitResponse(STATUS_SUBMIT_FAILED, submitResult);
	}

	private String buildSubmitFailMessage(String errorCode) {
		if ("BIND_NOT_FOUND".equals(errorCode)) {
			return "当前登录账号未绑定外部系统，请先完成绑定后再继续";
		}
		if ("TOKEN_EXCHANGE_FAILED".equals(errorCode) || "TOKEN_REFRESH_FAILED".equals(errorCode)) {
			return "外部系统令牌获取失败，请稍后重试或联系管理员";
		}
		return "提交失败，请检查参数或上游系统状态";
	}

	private Map<String, Object> toJsonFieldHints(Map<String, CapabilityProviderOrchestrator.FieldHint> fieldHints) {
		Map<String, Object> jsonHints = new LinkedHashMap<>();
		for (Map.Entry<String, CapabilityProviderOrchestrator.FieldHint> entry : fieldHints.entrySet()) {
			CapabilityProviderOrchestrator.FieldHint hint = entry.getValue();
			if (hint == null) {
				continue;
			}
			Map<String, Object> jsonHint = new LinkedHashMap<>();
			jsonHint.put("input_mode", hint.inputMode());
			jsonHint.put("next_cursor", hint.nextCursor());
			jsonHint.put("has_more", hint.hasMore());
			jsonHint.put("depends_on", hint.dependsOn() != null ? hint.dependsOn() : List.of());
			jsonHint.put("default_applied", hint.defaultApplied());
			if (hint.options() != null && !hint.options().isEmpty()) {
				List<Map<String, Object>> options = new ArrayList<>();
				for (CapabilityProviderOrchestrator.OptionItem option : hint.options()) {
					Map<String, Object> optionJson = new LinkedHashMap<>();
					optionJson.put("label", option.label());
					optionJson.put("value", option.value());
					optionJson.put("extra", option.extra() != null ? option.extra() : Map.of());
					options.add(optionJson);
				}
				jsonHint.put("options", options);
			}
			if (hint.defaultValue() != null) {
				Map<String, Object> defaultValue = new LinkedHashMap<>();
				defaultValue.put("label", hint.defaultValue().label());
				defaultValue.put("value", hint.defaultValue().value());
				defaultValue.put("extra", hint.defaultValue().extra() != null ? hint.defaultValue().extra() : Map.of());
				jsonHint.put("default_value", defaultValue);
			}
			jsonHints.put(entry.getKey(), jsonHint);
		}
		return jsonHints;
	}

	private Map<String, String> buildFieldDescriptionMap(List<String> fieldNames) {
		Map<String, String> descriptionMap = new LinkedHashMap<>();
		for (String fieldName : fieldNames) {
			CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
			if (fieldSpec != null && StringUtils.hasText(fieldSpec.getDescription())) {
				descriptionMap.put(fieldName, fieldSpec.getDescription());
			}
			else {
				descriptionMap.put(fieldName, "");
			}
		}
		return descriptionMap;
	}

	private Map<String, String> buildFieldLabelMap(List<String> fieldNames) {
		Map<String, String> labelMap = new LinkedHashMap<>();
		for (String fieldName : fieldNames) {
			labelMap.put(fieldName, defaultFieldLabel(fieldName));
		}
		return labelMap;
	}

	private String buildMissingFieldPrompt(List<String> missingFields,
			Map<String, Object> mergedArgs,
			Map<String, CapabilityProviderOrchestrator.FieldHint> fieldHints) {
		List<String> askQueue = buildActionableMissingFields(missingFields, mergedArgs);
		if (!askQueue.isEmpty()) {
			String nextField = askQueue.get(0);
			String label = defaultFieldLabel(nextField);
			CapabilityRegistrationProperties.FieldSpec nextSpec = fieldSpecByName.get(nextField);
			List<Map<String, Object>> options = buildPlanOptions(nextField, nextSpec,
					fieldHints != null ? fieldHints.get(nextField) : null);

			StringBuilder prompt = new StringBuilder("请先补充【").append(label).append("】");
			if (nextSpec != null && StringUtils.hasText(nextSpec.getDescription())) {
				prompt.append("（").append(nextSpec.getDescription()).append("）");
			}
			if (!options.isEmpty()) {
				prompt.append("。可选：");
				prompt.append(options.stream()
						.map(option -> {
							String value = String.valueOf(option.get("value"));
							String optionLabel = String.valueOf(option.get("label"));
							return optionLabel + "(" + value + ")";
						})
						.collect(Collectors.joining("、")));
			}
			if (askQueue.size() > 1) {
				List<String> followUps = askQueue.stream().skip(1).limit(3).map(this::defaultFieldLabel).toList();
				prompt.append("。完成后我会继续确认：").append(String.join("、", followUps));
				if (askQueue.size() > 4) {
					prompt.append(" 等字段");
				}
			}
			prompt.append("。");
			return prompt.toString();
		}

		StringBuilder sb = new StringBuilder("还差一点就可以继续了，请补充以下信息：\n\n");
		if (missingFields == null || missingFields.isEmpty()) {
			sb.append("- 请继续补充必填信息");
			return sb.toString();
		}
		if (missingFields.size() == 1) {
			String fieldName = missingFields.get(0);
			String label = defaultFieldLabel(fieldName);
			CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
			sb = new StringBuilder("请先补充【").append(label).append("】");
			if (fieldSpec != null && StringUtils.hasText(fieldSpec.getDescription())) {
				sb.append("（").append(fieldSpec.getDescription()).append("）");
			}
			List<Map<String, Object>> options = buildPlanOptions(fieldName,
					fieldSpec,
					fieldHints != null ? fieldHints.get(fieldName) : null);
			if (!options.isEmpty()) {
				sb.append("。可选：");
				sb.append(options.stream()
						.map(option -> {
							String value = String.valueOf(option.get("value"));
							String optionLabel = String.valueOf(option.get("label"));
							return optionLabel + "(" + value + ")";
						})
						.collect(Collectors.joining("、")));
			}
			sb.append("。");
			return sb.toString();
		}
		for (String fieldName : missingFields) {
			CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
			String label = defaultFieldLabel(fieldName);
			sb.append("- ").append(label).append(" (").append(fieldName).append(")");
			if (fieldSpec != null && StringUtils.hasText(fieldSpec.getDescription())) {
				sb.append(" - ").append(fieldSpec.getDescription());
			}
			sb.append("\n");
		}
		sb.append("\n你也可以按“字段: 值”一次性回复多项信息。");
		return sb.toString().trim();
	}

	private Map<String, Object> buildCollectQuestionPlan(List<String> missingFields, Map<String, Object> mergedArgs,
			Map<String, CapabilityProviderOrchestrator.FieldHint> fieldHints) {
		List<String> askQueue = buildActionableMissingFields(missingFields, mergedArgs);
		List<String> effectiveQueue = !askQueue.isEmpty()
				? askQueue
				: (missingFields != null ? missingFields : List.of());

		Map<String, Object> plan = new LinkedHashMap<>();
		plan.put("strategy", "CONFIG_DRIVEN_SLOT_FILLING");
		plan.put("step", "COLLECT");
		plan.put("tool_name", toolDefinition.name());
		plan.put("missing_count", missingFields != null ? missingFields.size() : 0);
		plan.put("missing_fields", missingFields != null ? missingFields : List.of());
		plan.put("ask_queue", effectiveQueue);
		plan.put("collected_slots", mergedArgs != null ? mergedArgs : Map.of());

		List<Map<String, Object>> fields = new ArrayList<>();
		for (String fieldName : effectiveQueue) {
				CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
				CapabilityProviderOrchestrator.FieldHint fieldHint = fieldHints != null ? fieldHints.get(fieldName) : null;
				Map<String, Object> fieldPlan = new LinkedHashMap<>();
				fieldPlan.put("name", fieldName);
				fieldPlan.put("label", defaultFieldLabel(fieldName));
				fieldPlan.put("required", true);
				if (fieldSpec != null) {
					fieldPlan.put("description", fieldSpec.getDescription());
					fieldPlan.put("input_mode", fieldSpec.getInputMode());
					fieldPlan.put("ask_mode", fieldSpec.getAskMode());
					fieldPlan.put("depends_on", fieldSpec.getDependsOn());
					fieldPlan.put("auto_fill_action", fieldSpec.getDefaultValueAction());
					fieldPlan.put("infer_mode", fieldSpec.getInferMode());
					if (StringUtils.hasText(fieldSpec.getInferPrompt())) {
						fieldPlan.put("infer_prompt", fieldSpec.getInferPrompt());
					}
				}
				fieldPlan.put("options", buildPlanOptions(fieldName, fieldSpec, fieldHint));
				fieldPlan.put("default_value", buildDefaultValue(fieldSpec, fieldHint));
				fields.add(fieldPlan);
		}
		plan.put("fields", fields);
		if (!fields.isEmpty()) {
			Map<String, Object> first = fields.get(0);
			plan.put("next_field", Map.of(
					"name", first.get("name"),
					"label", first.get("label")));
		}
		return plan;
	}

	private List<String> buildActionableMissingFields(List<String> missingFields, @Nullable Map<String, Object> mergedArgs) {
		if (missingFields == null || missingFields.isEmpty()) {
			return List.of();
		}
		Map<String, Object> safeSlots = mergedArgs != null ? mergedArgs : Map.of();
		List<String> actionable = new ArrayList<>();
		for (String fieldName : missingFields) {
			CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
			if (fieldSpec == null || fieldSpec.getDependsOn() == null || fieldSpec.getDependsOn().isEmpty()) {
				actionable.add(fieldName);
				continue;
			}
			boolean dependencyReady = true;
			for (String dependency : fieldSpec.getDependsOn()) {
				if (isEmptyValue(safeSlots.get(dependency))) {
					dependencyReady = false;
					break;
				}
			}
			if (dependencyReady) {
				actionable.add(fieldName);
			}
		}
		if (actionable.isEmpty()) {
			return List.of(missingFields.get(0));
		}

		Set<String> headerArgNames = new HashSet<>(headerArgs.values());
		actionable.sort(Comparator.comparingInt(fieldName -> askPriority(fieldName, headerArgNames)));
		return actionable;
	}

	private int askPriority(String fieldName, Set<String> headerArgNames) {
		int priority = 100;
		CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
		if (fieldSpec != null && "BATCH".equalsIgnoreCase(fieldSpec.getAskMode())) {
			priority += 20;
		}
		if (fieldSpec != null && StringUtils.hasText(fieldSpec.getDefaultValueAction())) {
			priority += 10;
		}
		if (headerArgNames.contains(fieldName)) {
			priority += 200;
		}
		return priority;
	}

	private Map<String, Object> buildConfirmQuestionPlan(Map<String, Object> mergedArgs) {
		Map<String, Object> plan = new LinkedHashMap<>();
		plan.put("strategy", "CONFIG_DRIVEN_SLOT_FILLING");
		plan.put("step", "CONFIRM");
		plan.put("tool_name", toolDefinition.name());
		plan.put("confirmation_arg_name", confirmationArgName);
		plan.put("preview", mergedArgs != null ? mergedArgs : Map.of());

		List<Map<String, Object>> fields = new ArrayList<>();
		for (Map.Entry<String, Object> entry : (mergedArgs != null ? mergedArgs : Map.<String, Object>of()).entrySet()) {
			Map<String, Object> field = new LinkedHashMap<>();
			field.put("name", entry.getKey());
			field.put("label", defaultFieldLabel(entry.getKey()));
			field.put("value", entry.getValue());
			fields.add(field);
		}
		plan.put("fields", fields);
		return plan;
	}

	private List<Map<String, Object>> buildPlanOptions(String fieldName,
			@Nullable CapabilityRegistrationProperties.FieldSpec fieldSpec,
			@Nullable CapabilityProviderOrchestrator.FieldHint fieldHint) {
		if (fieldHint != null && fieldHint.options() != null && !fieldHint.options().isEmpty()) {
			List<Map<String, Object>> options = new ArrayList<>();
			for (CapabilityProviderOrchestrator.OptionItem item : fieldHint.options()) {
				Map<String, Object> option = new LinkedHashMap<>();
				option.put("label", item.label());
				option.put("value", item.value());
				option.put("extra", item.extra() != null ? item.extra() : Map.of());
				options.add(option);
			}
			return options;
		}
		if (fieldSpec != null && fieldSpec.getOptions() != null && !fieldSpec.getOptions().isEmpty()) {
			List<Map<String, Object>> options = new ArrayList<>();
			for (CapabilityRegistrationProperties.FieldOption item : fieldSpec.getOptions()) {
				if (item == null || !StringUtils.hasText(item.getValue())) {
					continue;
				}
				Map<String, Object> option = new LinkedHashMap<>();
				option.put("label", StringUtils.hasText(item.getLabel()) ? item.getLabel() : item.getValue());
				option.put("value", item.getValue());
				if (StringUtils.hasText(item.getDescription())) {
					option.put("description", item.getDescription());
				}
				options.add(option);
			}
			return options;
		}
		return List.of();
	}

	private Object buildDefaultValue(@Nullable CapabilityRegistrationProperties.FieldSpec fieldSpec,
			@Nullable CapabilityProviderOrchestrator.FieldHint fieldHint) {
		if (fieldHint != null && fieldHint.defaultValue() != null && StringUtils.hasText(fieldHint.defaultValue().value())) {
			Map<String, Object> defaultValue = new LinkedHashMap<>();
			defaultValue.put("label", fieldHint.defaultValue().label());
			defaultValue.put("value", fieldHint.defaultValue().value());
			defaultValue.put("extra", fieldHint.defaultValue().extra() != null ? fieldHint.defaultValue().extra() : Map.of());
			return defaultValue;
		}
		if (fieldSpec != null && StringUtils.hasText(fieldSpec.getDefaultValue())) {
			return fieldSpec.getDefaultValue();
		}
		return null;
	}

	private String defaultFieldLabel(String fieldName) {
		CapabilityRegistrationProperties.FieldSpec fieldSpec = fieldSpecByName.get(fieldName);
		if (fieldSpec != null && StringUtils.hasText(fieldSpec.getDescription())) {
			String text = fieldSpec.getDescription().trim();
			int cutAt = text.length();
			int[] indexes = new int[] {
					text.indexOf('（'),
					text.indexOf('('),
					text.indexOf('，'),
					text.indexOf(','),
					text.indexOf('。'),
					text.indexOf('：'),
					text.indexOf(':')
			};
			for (int index : indexes) {
				if (index > 0 && index < cutAt) {
					cutAt = index;
				}
			}
			String label = text.substring(0, cutAt).trim();
			if (StringUtils.hasText(label)) {
				return label;
			}
		}
		return DEFAULT_FIELD_LABELS.getOrDefault(fieldName, fieldName);
	}

	private static Map<String, String> createDefaultFieldLabels() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("to_unames", "汇报对象姓名");
		labels.put("to_uids", "汇报对象ID");
		labels.put("types", "请假类型");
		labels.put("reason", "请假原因");
		labels.put("duration", "请假时长");
		labels.put("end_date", "结束日期");
		labels.put("check_flow_id", "审批流程");
		labels.put("check_uids", "审批人");
		labels.put("check_copy_uids", "抄送人");
		labels.put("room_id", "会议室");
		labels.put("title", "会议主题");
		labels.put("num", "参会人数");
		labels.put("requirement", "会议需求");
		labels.put("join_uids", "参会人员");
		labels.put("leave_type", "请假类型");
		labels.put("leave_days", "请假天数");
		labels.put("start_date", "工作日期");
		labels.put("works", "今日工作内容");
		labels.put("plans", "明日工作计划");
		labels.put("cookie", "认证Cookie");
		labels.put("remark", "备注");
		labels.put("report_department_id", "汇报部门");
		labels.put("report_user_ids", "汇报对象");
		labels.put("approver_department_id", "审批部门");
		labels.put("approver_user_id", "审批人");
		return Collections.unmodifiableMap(labels);
	}

	private Object tryParseJson(String responseBody) {
		if (!StringUtils.hasText(responseBody)) {
			return "";
		}
		try {
			return objectMapper.readValue(responseBody, Object.class);
		}
		catch (Exception ignored) {
			return responseBody;
		}
	}

	private String toJson(Map<String, Object> response) {
		try {
			return objectMapper.writeValueAsString(response);
		}
		catch (Exception e) {
			return "{\"status\":\"ERROR\",\"message\":\"Failed to serialize tool response\"}";
		}
	}

	private static class SubmissionResult {

		private final boolean success;

		private final int httpStatus;

		private final String body;

		private final String errorCode;

		private SubmissionResult(boolean success, int httpStatus, String body, String errorCode) {
			this.success = success;
			this.httpStatus = httpStatus;
			this.body = body;
			this.errorCode = errorCode;
		}

	}

}
