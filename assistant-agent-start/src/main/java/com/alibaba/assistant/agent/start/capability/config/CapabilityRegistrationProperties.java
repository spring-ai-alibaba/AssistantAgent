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
package com.alibaba.assistant.agent.start.capability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capability registration properties.
 *
 * <p>Configuration-driven capability registration for custom business tools.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "assistant.agent.capability")
public class CapabilityRegistrationProperties {

	/**
	 * Registered capabilities.
	 */
	private List<HttpFormCapability> registrations = new ArrayList<>();

	/**
	 * Tenant scoped provider registrations.
	 */
	private List<TenantProviderRegistration> tenantProviders = new ArrayList<>();

	public List<HttpFormCapability> getRegistrations() {
		return registrations;
	}

	public void setRegistrations(List<HttpFormCapability> registrations) {
		this.registrations = registrations != null ? registrations : new ArrayList<>();
	}

	public List<TenantProviderRegistration> getTenantProviders() {
		return tenantProviders;
	}

	public void setTenantProviders(List<TenantProviderRegistration> tenantProviders) {
		this.tenantProviders = tenantProviders != null ? tenantProviders : new ArrayList<>();
	}

	/**
	 * Configuration for a single HTTP form capability.
	 */
	public static class HttpFormCapability {

		private boolean enabled = false;

		private String toolName;

		private String description = "";

		private String targetClassName = "business_tools";

		private String targetClassDescription = "Business capability tools";

		private String endpointUrl;

		/**
		 * Optional provider code. When configured, submit/options/default/resolve
		 * calls are routed to tenant provider service.
		 */
		private String providerCode;

		/**
		 * Provider submit action name.
		 */
		private String submitAction = "submit";

		private String method = "POST";

		private String contentType = "application/x-www-form-urlencoded; charset=UTF-8";

		private long connectTimeoutMs = 5000;

		private long readTimeoutMs = 10000;

		/**
		 * Enable slot-filling behavior for incremental information collection.
		 */
		private boolean slotFillingEnabled = false;

		/**
		 * Require explicit confirmation before submission.
		 */
		private boolean confirmationRequired = false;

		/**
		 * Input argument name for confirmation flag.
		 */
		private String confirmationArgName = "confirmed";

		/**
		 * Static HTTP headers always attached to requests.
		 */
		private Map<String, String> headers = new LinkedHashMap<>();

		/**
		 * Header mapping: headerName -> inputArgName.
		 *
		 * <p>Example: Cookie -> cookie
		 */
		private Map<String, String> headerArgs = new LinkedHashMap<>();

		/**
		 * Default form parameters.
		 */
		private Map<String, String> defaultFormData = new LinkedHashMap<>();

		/**
		 * Explicit form field names to read from input args.
		 */
		private List<String> formFieldNames = new ArrayList<>();

		/**
		 * Input field definitions used to generate tool input schema.
		 */
		private List<FieldSpec> fields = new ArrayList<>();

		/**
		 * Keywords used to match operation intent for this capability.
		 */
		private List<String> operationIntentKeywords = new ArrayList<>();

		/**
		 * Regex patterns used to match operation intent for this capability.
		 */
		private List<String> operationIntentPatterns = new ArrayList<>();

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getToolName() {
			return toolName;
		}

		public void setToolName(String toolName) {
			this.toolName = toolName;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getTargetClassName() {
			return targetClassName;
		}

		public void setTargetClassName(String targetClassName) {
			this.targetClassName = targetClassName;
		}

		public String getTargetClassDescription() {
			return targetClassDescription;
		}

		public void setTargetClassDescription(String targetClassDescription) {
			this.targetClassDescription = targetClassDescription;
		}

		public String getEndpointUrl() {
			return endpointUrl;
		}

		public void setEndpointUrl(String endpointUrl) {
			this.endpointUrl = endpointUrl;
		}

		public String getProviderCode() {
			return providerCode;
		}

		public void setProviderCode(String providerCode) {
			this.providerCode = providerCode;
		}

		public String getSubmitAction() {
			return submitAction;
		}

		public void setSubmitAction(String submitAction) {
			this.submitAction = submitAction;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public String getContentType() {
			return contentType;
		}

		public void setContentType(String contentType) {
			this.contentType = contentType;
		}

		public long getConnectTimeoutMs() {
			return connectTimeoutMs;
		}

		public void setConnectTimeoutMs(long connectTimeoutMs) {
			this.connectTimeoutMs = connectTimeoutMs;
		}

		public long getReadTimeoutMs() {
			return readTimeoutMs;
		}

		public void setReadTimeoutMs(long readTimeoutMs) {
			this.readTimeoutMs = readTimeoutMs;
		}

		public boolean isSlotFillingEnabled() {
			return slotFillingEnabled;
		}

		public void setSlotFillingEnabled(boolean slotFillingEnabled) {
			this.slotFillingEnabled = slotFillingEnabled;
		}

		public boolean isConfirmationRequired() {
			return confirmationRequired;
		}

		public void setConfirmationRequired(boolean confirmationRequired) {
			this.confirmationRequired = confirmationRequired;
		}

		public String getConfirmationArgName() {
			return confirmationArgName;
		}

		public void setConfirmationArgName(String confirmationArgName) {
			this.confirmationArgName = confirmationArgName;
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers != null ? headers : new LinkedHashMap<>();
		}

		public Map<String, String> getHeaderArgs() {
			return headerArgs;
		}

		public void setHeaderArgs(Map<String, String> headerArgs) {
			this.headerArgs = headerArgs != null ? headerArgs : new LinkedHashMap<>();
		}

		public Map<String, String> getDefaultFormData() {
			return defaultFormData;
		}

		public void setDefaultFormData(Map<String, String> defaultFormData) {
			this.defaultFormData = defaultFormData != null ? defaultFormData : new LinkedHashMap<>();
		}

		public List<String> getFormFieldNames() {
			return formFieldNames;
		}

		public void setFormFieldNames(List<String> formFieldNames) {
			this.formFieldNames = formFieldNames != null ? formFieldNames : new ArrayList<>();
		}

		public List<FieldSpec> getFields() {
			return fields;
		}

		public void setFields(List<FieldSpec> fields) {
			this.fields = fields != null ? fields : new ArrayList<>();
		}

		public List<String> getOperationIntentKeywords() {
			return operationIntentKeywords;
		}

		public void setOperationIntentKeywords(List<String> operationIntentKeywords) {
			this.operationIntentKeywords = operationIntentKeywords != null ? operationIntentKeywords : new ArrayList<>();
		}

		public List<String> getOperationIntentPatterns() {
			return operationIntentPatterns;
		}

		public void setOperationIntentPatterns(List<String> operationIntentPatterns) {
			this.operationIntentPatterns = operationIntentPatterns != null ? operationIntentPatterns : new ArrayList<>();
		}

	}

	/**
	 * Input field specification for tool schema.
	 */
	public static class FieldSpec {

		private String name;

		private String type = "string";

		private String description = "";

		private boolean required = false;

		private String defaultValue;

		/**
		 * Interaction mode. Available values: TEXT, SELECT_SINGLE, SELECT_MULTI.
		 */
		private String inputMode = "TEXT";

		/**
		 * Ask mode for dialog planning. Available values: AUTO, SINGLE, BATCH.
		 */
		private String askMode = "AUTO";

		/**
		 * Static options configured by capability definition.
		 * Useful for enum-like fields such as types/send.
		 */
		private List<FieldOption> options = new ArrayList<>();

		/**
		 * Slot inference mode. Available values:
		 * AUTO (default), LLM, NONE/OFF.
		 */
		private String inferMode = "AUTO";

		/**
		 * Optional hint for LLM inference prompt.
		 */
		private String inferPrompt = "";

		/**
		 * Provider action for options query.
		 */
		private String optionQueryAction;

		/**
		 * Provider action for resolving display text to business entity value.
		 */
		private String entityResolveAction;

		/**
		 * Provider action for default value lookup.
		 */
		private String defaultValueAction;

		/**
		 * Dependencies for chained selections.
		 */
		private List<String> dependsOn = new ArrayList<>();

		/**
		 * Page size when querying options from provider.
		 */
		private int optionPageSize = 20;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public boolean isRequired() {
			return required;
		}

		public void setRequired(boolean required) {
			this.required = required;
		}

		public String getDefaultValue() {
			return defaultValue;
		}

		public void setDefaultValue(String defaultValue) {
			this.defaultValue = defaultValue;
		}

		public String getInputMode() {
			return inputMode;
		}

		public void setInputMode(String inputMode) {
			this.inputMode = inputMode;
		}

		public String getAskMode() {
			return askMode;
		}

		public void setAskMode(String askMode) {
			this.askMode = askMode;
		}

		public List<FieldOption> getOptions() {
			return options;
		}

		public void setOptions(List<FieldOption> options) {
			this.options = options != null ? options : new ArrayList<>();
		}

		public String getInferMode() {
			return inferMode;
		}

		public void setInferMode(String inferMode) {
			this.inferMode = inferMode;
		}

		public String getInferPrompt() {
			return inferPrompt;
		}

		public void setInferPrompt(String inferPrompt) {
			this.inferPrompt = inferPrompt;
		}

		public String getOptionQueryAction() {
			return optionQueryAction;
		}

		public void setOptionQueryAction(String optionQueryAction) {
			this.optionQueryAction = optionQueryAction;
		}

		public String getEntityResolveAction() {
			return entityResolveAction;
		}

		public void setEntityResolveAction(String entityResolveAction) {
			this.entityResolveAction = entityResolveAction;
		}

		public String getDefaultValueAction() {
			return defaultValueAction;
		}

		public void setDefaultValueAction(String defaultValueAction) {
			this.defaultValueAction = defaultValueAction;
		}

		public List<String> getDependsOn() {
			return dependsOn;
		}

		public void setDependsOn(List<String> dependsOn) {
			this.dependsOn = dependsOn != null ? dependsOn : new ArrayList<>();
		}

		public int getOptionPageSize() {
			return optionPageSize;
		}

		public void setOptionPageSize(int optionPageSize) {
			this.optionPageSize = optionPageSize > 0 ? optionPageSize : 20;
		}

	}

	/**
	 * Static selectable option in field definition.
	 */
	public static class FieldOption {

		private String label;

		private String value;

		private String description = "";

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

	}

	/**
	 * Tenant scoped provider registrations.
	 */
	public static class TenantProviderRegistration {

		private String tenantId = "default";

		private List<ProviderRegistration> providers = new ArrayList<>();

		public String getTenantId() {
			return tenantId;
		}

		public void setTenantId(String tenantId) {
			this.tenantId = tenantId;
		}

		public List<ProviderRegistration> getProviders() {
			return providers;
		}

		public void setProviders(List<ProviderRegistration> providers) {
			this.providers = providers != null ? providers : new ArrayList<>();
		}

	}

	/**
	 * Provider registration for a tenant.
	 */
	public static class ProviderRegistration {

		private boolean enabled = true;

		private String providerCode;

		private String providerVersion = "v1";

		private String baseUrl;

		private String optionsQueryPath = "/capability/provider/options/query";

		private String entityResolvePath = "/capability/provider/entity/resolve";

		private String defaultValuePath = "/capability/provider/default/value";

		private String submitPath = "/capability/provider/submit";

		private String tokenExchangePath = "/oauth/token/exchange";

		private String tokenRefreshPath = "/oauth/token/refresh";

		private long connectTimeoutMs = 5000;

		private long readTimeoutMs = 10000;

		private Map<String, String> headers = new LinkedHashMap<>();

		private ProviderAuthConfig auth = new ProviderAuthConfig();

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getProviderCode() {
			return providerCode;
		}

		public void setProviderCode(String providerCode) {
			this.providerCode = providerCode;
		}

		public String getProviderVersion() {
			return providerVersion;
		}

		public void setProviderVersion(String providerVersion) {
			this.providerVersion = providerVersion;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getOptionsQueryPath() {
			return optionsQueryPath;
		}

		public void setOptionsQueryPath(String optionsQueryPath) {
			this.optionsQueryPath = optionsQueryPath;
		}

		public String getEntityResolvePath() {
			return entityResolvePath;
		}

		public void setEntityResolvePath(String entityResolvePath) {
			this.entityResolvePath = entityResolvePath;
		}

		public String getDefaultValuePath() {
			return defaultValuePath;
		}

		public void setDefaultValuePath(String defaultValuePath) {
			this.defaultValuePath = defaultValuePath;
		}

		public String getSubmitPath() {
			return submitPath;
		}

		public void setSubmitPath(String submitPath) {
			this.submitPath = submitPath;
		}

		public String getTokenExchangePath() {
			return tokenExchangePath;
		}

		public void setTokenExchangePath(String tokenExchangePath) {
			this.tokenExchangePath = tokenExchangePath;
		}

		public String getTokenRefreshPath() {
			return tokenRefreshPath;
		}

		public void setTokenRefreshPath(String tokenRefreshPath) {
			this.tokenRefreshPath = tokenRefreshPath;
		}

		public long getConnectTimeoutMs() {
			return connectTimeoutMs;
		}

		public void setConnectTimeoutMs(long connectTimeoutMs) {
			this.connectTimeoutMs = connectTimeoutMs;
		}

		public long getReadTimeoutMs() {
			return readTimeoutMs;
		}

		public void setReadTimeoutMs(long readTimeoutMs) {
			this.readTimeoutMs = readTimeoutMs;
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers != null ? headers : new LinkedHashMap<>();
		}

		public ProviderAuthConfig getAuth() {
			return auth;
		}

		public void setAuth(ProviderAuthConfig auth) {
			this.auth = auth != null ? auth : new ProviderAuthConfig();
		}

	}

	/**
	 * Provider auth settings.
	 */
	public static class ProviderAuthConfig {

		private String tokenHeaderName = "Authorization";

		private String tokenPrefix = "Bearer ";

		private String clientId;

		private String clientSecret;

		private String refreshTokenEncryptionKey = "assistant-agent-refresh-token-key";

		private long refreshAheadSeconds = 60;

		public String getTokenHeaderName() {
			return tokenHeaderName;
		}

		public void setTokenHeaderName(String tokenHeaderName) {
			this.tokenHeaderName = tokenHeaderName;
		}

		public String getTokenPrefix() {
			return tokenPrefix;
		}

		public void setTokenPrefix(String tokenPrefix) {
			this.tokenPrefix = tokenPrefix;
		}

		public String getClientId() {
			return clientId;
		}

		public void setClientId(String clientId) {
			this.clientId = clientId;
		}

		public String getClientSecret() {
			return clientSecret;
		}

		public void setClientSecret(String clientSecret) {
			this.clientSecret = clientSecret;
		}

		public String getRefreshTokenEncryptionKey() {
			return refreshTokenEncryptionKey;
		}

		public void setRefreshTokenEncryptionKey(String refreshTokenEncryptionKey) {
			this.refreshTokenEncryptionKey = refreshTokenEncryptionKey;
		}

		public long getRefreshAheadSeconds() {
			return refreshAheadSeconds;
		}

		public void setRefreshAheadSeconds(long refreshAheadSeconds) {
			this.refreshAheadSeconds = refreshAheadSeconds;
		}

	}

}
