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

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Access token resolve/exchange/refresh service.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class CapabilityProviderTokenService {

	private final CapabilityProviderConfigRegistry providerConfigRegistry;

	private final CapabilityUserBindingStore userBindingStore;

	private final CapabilityProviderClient providerClient;

	public CapabilityProviderTokenService(CapabilityProviderConfigRegistry providerConfigRegistry,
			CapabilityUserBindingStore userBindingStore, CapabilityProviderClient providerClient) {
		this.providerConfigRegistry = providerConfigRegistry;
		this.userBindingStore = userBindingStore;
		this.providerClient = providerClient;
	}

	public String resolveAccessToken(String tenantId, String userId, String providerCode) {
		CapabilityProviderConfig providerConfig = providerConfigRegistry.getRequired(tenantId, providerCode);
		CapabilityUserBinding binding = userBindingStore.find(tenantId, userId, providerCode)
				.orElseThrow(() -> new CapabilityProviderException(
						CapabilityProviderErrorCode.BIND_NOT_FOUND,
						"external binding not found, tenant=%s, user=%s, provider=%s".formatted(tenantId, userId,
								providerCode)));

		if (isAccessTokenUsable(binding, providerConfig)) {
			return binding.getAccessToken();
		}

		if (StringUtils.hasText(binding.getEncryptedRefreshToken())) {
			Map<String, Object> request = new LinkedHashMap<>();
			request.put("grant_type", "refresh_token");
			request.put("refresh_token", TokenCryptoSupport.decrypt(binding.getEncryptedRefreshToken(),
					providerConfig.getRefreshTokenEncryptionKey()));
			request.put("external_user_id", binding.getExternalUserId());
			putClientCredential(request, providerConfig);
			Map<String, Object> response = providerClient.postJson(providerConfig, providerConfig.getTokenRefreshPath(),
					request, Map.of());
			return applyTokenResponse(binding, providerConfig, response, CapabilityProviderErrorCode.TOKEN_REFRESH_FAILED);
		}

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("grant_type", "external_user");
		request.put("external_user_id", binding.getExternalUserId());
		putClientCredential(request, providerConfig);
		Map<String, Object> response = providerClient.postJson(providerConfig, providerConfig.getTokenExchangePath(),
				request, Map.of());
		return applyTokenResponse(binding, providerConfig, response, CapabilityProviderErrorCode.TOKEN_EXCHANGE_FAILED);
	}

	private boolean isAccessTokenUsable(CapabilityUserBinding binding, CapabilityProviderConfig providerConfig) {
		if (!StringUtils.hasText(binding.getAccessToken())) {
			return false;
		}
		long expireAt = binding.getAccessTokenExpireAtEpochSeconds();
		if (expireAt <= 0) {
			return true;
		}
		long now = Instant.now().getEpochSecond();
		return expireAt - providerConfig.getRefreshAheadSeconds() > now;
	}

	@SuppressWarnings("unchecked")
	private String applyTokenResponse(CapabilityUserBinding binding, CapabilityProviderConfig providerConfig,
			Map<String, Object> response, CapabilityProviderErrorCode failedCode) {
		Map<String, Object> tokenBody = unwrapData(response);
		Object accessTokenObj = tokenBody.get("access_token");
		if (accessTokenObj == null || !StringUtils.hasText(String.valueOf(accessTokenObj))) {
			throw new CapabilityProviderException(failedCode, "provider token response missing access_token");
		}

		String accessToken = String.valueOf(accessTokenObj);
		String refreshToken = Optional.ofNullable(tokenBody.get("refresh_token")).map(String::valueOf).orElse(null);
		long expiresIn = parseLong(tokenBody.get("expires_in"), 3600);
		long expireAt = Instant.now().getEpochSecond() + Math.max(expiresIn, 60);

		binding.setAccessToken(accessToken);
		binding.setAccessTokenExpireAtEpochSeconds(expireAt);
		if (StringUtils.hasText(refreshToken)) {
			binding.setEncryptedRefreshToken(
					TokenCryptoSupport.encrypt(refreshToken, providerConfig.getRefreshTokenEncryptionKey()));
		}
		userBindingStore.save(binding);
		return accessToken;
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
		if (data instanceof Map<?, ?> map) {
			Map<String, Object> normalized = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() != null) {
					normalized.put(String.valueOf(entry.getKey()), entry.getValue());
				}
			}
			return normalized;
		}
		return response;
	}

	private long parseLong(Object value, long defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		try {
			return Long.parseLong(String.valueOf(value));
		}
		catch (Exception ignored) {
			return defaultValue;
		}
	}

	private void putClientCredential(Map<String, Object> request, CapabilityProviderConfig config) {
		if (StringUtils.hasText(config.getClientId())) {
			request.put("client_id", config.getClientId());
		}
		if (StringUtils.hasText(config.getClientSecret())) {
			request.put("client_secret", config.getClientSecret());
		}
	}

}

