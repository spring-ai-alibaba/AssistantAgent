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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Default HTTP implementation of provider client.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class DefaultCapabilityProviderClient implements CapabilityProviderClient {

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	public DefaultCapabilityProviderClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public Map<String, Object> postJson(CapabilityProviderConfig providerConfig, String path, Map<String, Object> payload,
			Map<String, String> headers) {
		try {
			String requestBody = objectMapper.writeValueAsString(payload != null ? payload : Map.of());
			HttpClient httpClient = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(providerConfig.getConnectTimeoutMs()))
					.build();
			HttpRequest.Builder builder = HttpRequest.newBuilder()
					.uri(URI.create(providerConfig.buildUrl(path)))
					.timeout(Duration.ofMillis(providerConfig.getReadTimeoutMs()))
					.header("Content-Type", "application/json; charset=UTF-8")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

			for (Map.Entry<String, String> entry : providerConfig.getHeaders().entrySet()) {
				builder.header(entry.getKey(), entry.getValue());
			}
			if (headers != null) {
				for (Map.Entry<String, String> entry : headers.entrySet()) {
					builder.header(entry.getKey(), entry.getValue());
				}
			}

			HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new CapabilityProviderException(
						CapabilityProviderErrorCode.PROVIDER_CALL_FAILED,
						"provider call failed, status=%s, path=%s".formatted(response.statusCode(), path));
			}
			String responseText = response.body();
			if (responseText == null || responseText.isBlank()) {
				return Map.of();
			}
			return objectMapper.readValue(responseText, MAP_TYPE);
		}
		catch (CapabilityProviderException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new CapabilityProviderException(
					CapabilityProviderErrorCode.PROVIDER_CALL_FAILED,
					"provider call failed, path=%s".formatted(path), ex);
		}
	}

}
