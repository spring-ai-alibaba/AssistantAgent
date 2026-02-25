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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime provider configuration.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CapabilityProviderConfig {

	private final String tenantId;

	private final String providerCode;

	private final String providerVersion;

	private final String baseUrl;

	private final String optionsQueryPath;

	private final String entityResolvePath;

	private final String defaultValuePath;

	private final String submitPath;

	private final String tokenExchangePath;

	private final String tokenRefreshPath;

	private final long connectTimeoutMs;

	private final long readTimeoutMs;

	private final Map<String, String> headers;

	private final String tokenHeaderName;

	private final String tokenPrefix;

	private final String clientId;

	private final String clientSecret;

	private final String refreshTokenEncryptionKey;

	private final long refreshAheadSeconds;

	public CapabilityProviderConfig(String tenantId, String providerCode, String providerVersion, String baseUrl,
			String optionsQueryPath, String entityResolvePath, String defaultValuePath, String submitPath,
			String tokenExchangePath, String tokenRefreshPath, long connectTimeoutMs, long readTimeoutMs,
			Map<String, String> headers, String tokenHeaderName, String tokenPrefix, String clientId,
			String clientSecret, String refreshTokenEncryptionKey, long refreshAheadSeconds) {
		this.tenantId = tenantId;
		this.providerCode = providerCode;
		this.providerVersion = providerVersion;
		this.baseUrl = baseUrl;
		this.optionsQueryPath = optionsQueryPath;
		this.entityResolvePath = entityResolvePath;
		this.defaultValuePath = defaultValuePath;
		this.submitPath = submitPath;
		this.tokenExchangePath = tokenExchangePath;
		this.tokenRefreshPath = tokenRefreshPath;
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
		this.headers = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<>();
		this.tokenHeaderName = tokenHeaderName;
		this.tokenPrefix = tokenPrefix;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.refreshTokenEncryptionKey = refreshTokenEncryptionKey;
		this.refreshAheadSeconds = refreshAheadSeconds;
	}

	public String getTenantId() {
		return tenantId;
	}

	public String getProviderCode() {
		return providerCode;
	}

	public String getProviderVersion() {
		return providerVersion;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public String getOptionsQueryPath() {
		return optionsQueryPath;
	}

	public String getEntityResolvePath() {
		return entityResolvePath;
	}

	public String getDefaultValuePath() {
		return defaultValuePath;
	}

	public String getSubmitPath() {
		return submitPath;
	}

	public String getTokenExchangePath() {
		return tokenExchangePath;
	}

	public String getTokenRefreshPath() {
		return tokenRefreshPath;
	}

	public long getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public long getReadTimeoutMs() {
		return readTimeoutMs;
	}

	public Map<String, String> getHeaders() {
		return Collections.unmodifiableMap(headers);
	}

	public String getTokenHeaderName() {
		return tokenHeaderName;
	}

	public String getTokenPrefix() {
		return tokenPrefix;
	}

	public String getClientId() {
		return clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public String getRefreshTokenEncryptionKey() {
		return refreshTokenEncryptionKey;
	}

	public long getRefreshAheadSeconds() {
		return refreshAheadSeconds;
	}

	public String buildUrl(String path) {
		if (path == null || path.isBlank()) {
			return baseUrl;
		}
		if (baseUrl.endsWith("/") && path.startsWith("/")) {
			return baseUrl.substring(0, baseUrl.length() - 1) + path;
		}
		if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
			return baseUrl + "/" + path;
		}
		return baseUrl + path;
	}

}

