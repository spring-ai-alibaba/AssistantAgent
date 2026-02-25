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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory binding store.
 *
 * <p>Production can replace this bean with DB-backed implementation.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class InMemoryCapabilityUserBindingStore implements CapabilityUserBindingStore {

	private final ConcurrentMap<String, CapabilityUserBinding> storage = new ConcurrentHashMap<>();

	@Override
	public Optional<CapabilityUserBinding> find(String tenantId, String userId, String providerCode) {
		return Optional.ofNullable(storage.get(key(tenantId, userId, providerCode)));
	}

	@Override
	public void save(CapabilityUserBinding binding) {
		if (binding == null || !StringUtils.hasText(binding.getProviderCode())) {
			return;
		}
		storage.put(key(binding.getTenantId(), binding.getUserId(), binding.getProviderCode()), binding);
	}

	private String key(String tenantId, String userId, String providerCode) {
		return normalize(tenantId) + "|" + normalize(userId) + "|" + normalize(providerCode);
	}

	private String normalize(String value) {
		return StringUtils.hasText(value) ? value.trim() : "";
	}

}

