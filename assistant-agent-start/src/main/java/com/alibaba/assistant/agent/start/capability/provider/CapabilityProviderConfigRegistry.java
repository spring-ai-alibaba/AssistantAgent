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

import com.alibaba.assistant.agent.start.capability.config.CapabilityRegistrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant scoped provider runtime registry.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class CapabilityProviderConfigRegistry {

	private static final Logger log = LoggerFactory.getLogger(CapabilityProviderConfigRegistry.class);

	private static final String DEFAULT_TENANT = "default";

	private final Map<String, Map<String, CapabilityProviderConfig>> registry = new ConcurrentHashMap<>();

	public CapabilityProviderConfigRegistry(CapabilityRegistrationProperties properties) {
		loadFromProperties(properties);
	}

	public Optional<CapabilityProviderConfig> find(String tenantId, String providerCode) {
		if (!StringUtils.hasText(providerCode)) {
			return Optional.empty();
		}
		String normalizedTenant = normalizeTenant(tenantId);
		CapabilityProviderConfig exact = registry
				.getOrDefault(normalizedTenant, Map.of())
				.get(providerCode);
		if (exact != null) {
			return Optional.of(exact);
		}
		CapabilityProviderConfig fallback = registry
				.getOrDefault(DEFAULT_TENANT, Map.of())
				.get(providerCode);
		return Optional.ofNullable(fallback);
	}

	public CapabilityProviderConfig getRequired(String tenantId, String providerCode) {
		return find(tenantId, providerCode)
				.orElseThrow(() -> new CapabilityProviderException(
						CapabilityProviderErrorCode.PROVIDER_NOT_FOUND,
						"provider not found, tenant=%s, provider=%s".formatted(tenantId, providerCode)));
	}

	public void register(String tenantId, CapabilityProviderConfig config) {
		if (config == null || !StringUtils.hasText(config.getProviderCode())) {
			return;
		}
		String normalizedTenant = normalizeTenant(tenantId);
		registry.computeIfAbsent(normalizedTenant, key -> new ConcurrentHashMap<>())
				.put(config.getProviderCode(), config);
		log.info("CapabilityProviderConfigRegistry#register - reason=provider registered, tenantId={}, providerCode={}",
				normalizedTenant, config.getProviderCode());
	}

	private void loadFromProperties(CapabilityRegistrationProperties properties) {
		if (properties == null || properties.getTenantProviders() == null) {
			return;
		}
		for (CapabilityRegistrationProperties.TenantProviderRegistration tenant : properties.getTenantProviders()) {
			if (tenant == null) {
				continue;
			}
			String tenantId = normalizeTenant(tenant.getTenantId());
			Map<String, CapabilityProviderConfig> providerMap =
					registry.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>());
			if (tenant.getProviders() == null) {
				continue;
			}
			for (CapabilityRegistrationProperties.ProviderRegistration provider : tenant.getProviders()) {
				CapabilityProviderConfig config = toConfig(tenantId, provider);
				if (config == null) {
					continue;
				}
				providerMap.put(config.getProviderCode(), config);
				log.info(
						"CapabilityProviderConfigRegistry#loadFromProperties - reason=provider loaded, tenantId={}, providerCode={}",
						tenantId, config.getProviderCode());
			}
		}
	}

	private CapabilityProviderConfig toConfig(String tenantId,
			CapabilityRegistrationProperties.ProviderRegistration provider) {
		if (provider == null || !provider.isEnabled() || !StringUtils.hasText(provider.getProviderCode())
				|| !StringUtils.hasText(provider.getBaseUrl())) {
			return null;
		}

		CapabilityRegistrationProperties.ProviderAuthConfig auth = provider.getAuth();
		if (auth == null) {
			auth = new CapabilityRegistrationProperties.ProviderAuthConfig();
		}

		return new CapabilityProviderConfig(
				tenantId,
				provider.getProviderCode(),
				provider.getProviderVersion(),
				provider.getBaseUrl(),
				provider.getOptionsQueryPath(),
				provider.getEntityResolvePath(),
				provider.getDefaultValuePath(),
				provider.getSubmitPath(),
				provider.getTokenExchangePath(),
				provider.getTokenRefreshPath(),
				provider.getConnectTimeoutMs() > 0 ? provider.getConnectTimeoutMs() : 5000,
				provider.getReadTimeoutMs() > 0 ? provider.getReadTimeoutMs() : 10000,
				provider.getHeaders() != null ? new LinkedHashMap<>(provider.getHeaders()) : Map.of(),
				StringUtils.hasText(auth.getTokenHeaderName()) ? auth.getTokenHeaderName() : "Authorization",
				auth.getTokenPrefix() != null ? auth.getTokenPrefix() : "",
				auth.getClientId(),
				auth.getClientSecret(),
				StringUtils.hasText(auth.getRefreshTokenEncryptionKey())
						? auth.getRefreshTokenEncryptionKey()
						: "assistant-agent-refresh-token-key",
				auth.getRefreshAheadSeconds() > 0 ? auth.getRefreshAheadSeconds() : 60
		);
	}

	private String normalizeTenant(String tenantId) {
		return StringUtils.hasText(tenantId) ? tenantId.trim() : DEFAULT_TENANT;
	}

}

