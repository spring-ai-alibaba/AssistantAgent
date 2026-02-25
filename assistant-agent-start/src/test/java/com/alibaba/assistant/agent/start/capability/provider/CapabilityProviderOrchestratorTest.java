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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityProviderOrchestratorTest {

    @Test
    void shouldPassFieldCursorToProviderWhenQueryingOptions() {
        CapabilityRegistrationProperties properties = buildProperties();
        CapabilityProviderConfigRegistry registry = new CapabilityProviderConfigRegistry(properties);
        InMemoryCapabilityUserBindingStore bindingStore = new InMemoryCapabilityUserBindingStore();
        CapabilityUserBinding binding = new CapabilityUserBinding();
        binding.setTenantId("tenant-a");
        binding.setUserId("user-a");
        binding.setProviderCode("oa-provider");
        binding.setExternalUserId("oa-user-1001");
        binding.setAccessToken("valid-token");
        binding.setAccessTokenExpireAtEpochSeconds(Instant.now().plusSeconds(600).getEpochSecond());
        bindingStore.save(binding);

        RecordingCapabilityProviderClient providerClient = new RecordingCapabilityProviderClient();
        providerClient.addResponse("/capability/provider/options/query", Map.of(
                "code", "OK",
                "data", Map.of(
                        "items", List.of(Map.of("label", "研发部", "value", "dept-rd")),
                        "cursor", "next-cursor",
                        "has_more", true
                )));

        CapabilityProviderTokenService tokenService = new CapabilityProviderTokenService(
                registry, bindingStore, providerClient);
        CapabilityProviderOrchestrator orchestrator = new CapabilityProviderOrchestrator(
                registry, tokenService, providerClient);

        CapabilityRegistrationProperties.HttpFormCapability capability =
                new CapabilityRegistrationProperties.HttpFormCapability();
        capability.setProviderCode("oa-provider");
        capability.setToolName("submit_office_work_report");
        CapabilityRegistrationProperties.FieldSpec fieldSpec = new CapabilityRegistrationProperties.FieldSpec();
        fieldSpec.setName("report_department_id");
        fieldSpec.setInputMode("SELECT_SINGLE");
        fieldSpec.setOptionQueryAction("query_departments");
        capability.setFields(List.of(fieldSpec));

        Map<String, Object> currentArgs = new LinkedHashMap<>();
        currentArgs.put("report_department_id_cursor", "cursor-1");
        CapabilityProviderOrchestrator.FieldHints hints = orchestrator.queryFieldHints(
                capability,
                List.of("report_department_id"),
                currentArgs,
                "tenant-a",
                "user-a");

        assertThat(providerClient.lastPayload.get("cursor")).isEqualTo("cursor-1");
        assertThat(hints.hintsByField()).containsKey("report_department_id");
        assertThat(hints.hintsByField().get("report_department_id").nextCursor()).isEqualTo("next-cursor");
    }

    private CapabilityRegistrationProperties buildProperties() {
        CapabilityRegistrationProperties properties = new CapabilityRegistrationProperties();
        CapabilityRegistrationProperties.ProviderRegistration providerRegistration =
                new CapabilityRegistrationProperties.ProviderRegistration();
        providerRegistration.setEnabled(true);
        providerRegistration.setProviderCode("oa-provider");
        providerRegistration.setBaseUrl("http://provider.test");
        providerRegistration.setOptionsQueryPath("/capability/provider/options/query");
        CapabilityRegistrationProperties.ProviderAuthConfig authConfig =
                new CapabilityRegistrationProperties.ProviderAuthConfig();
        authConfig.setTokenHeaderName("Authorization");
        authConfig.setTokenPrefix("Bearer ");
        providerRegistration.setAuth(authConfig);

        CapabilityRegistrationProperties.TenantProviderRegistration tenantProviderRegistration =
                new CapabilityRegistrationProperties.TenantProviderRegistration();
        tenantProviderRegistration.setTenantId("tenant-a");
        tenantProviderRegistration.setProviders(List.of(providerRegistration));
        properties.setTenantProviders(List.of(tenantProviderRegistration));
        return properties;
    }

    private static final class RecordingCapabilityProviderClient implements CapabilityProviderClient {

        private final Map<String, Map<String, Object>> responsesByPath = new LinkedHashMap<>();
        private Map<String, Object> lastPayload = Map.of();

        @Override
        public Map<String, Object> postJson(CapabilityProviderConfig providerConfig, String path,
                Map<String, Object> payload, Map<String, String> headers) {
            this.lastPayload = payload != null ? payload : Map.of();
            return responsesByPath.getOrDefault(path, Map.of());
        }

        private void addResponse(String path, Map<String, Object> response) {
            responsesByPath.put(path, response);
        }

    }

}

