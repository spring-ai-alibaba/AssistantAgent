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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityProviderTokenServiceTest {

    @Test
    void shouldReturnBindNotFoundWhenExternalBindingIsMissing() {
        CapabilityProviderConfigRegistry registry = new CapabilityProviderConfigRegistry(buildProperties());
        InMemoryCapabilityUserBindingStore bindingStore = new InMemoryCapabilityUserBindingStore();
        RecordingCapabilityProviderClient providerClient = new RecordingCapabilityProviderClient();
        CapabilityProviderTokenService tokenService = new CapabilityProviderTokenService(
                registry, bindingStore, providerClient);

        assertThatThrownBy(() -> tokenService.resolveAccessToken("tenant-a", "user-a", "oa-provider"))
                .isInstanceOf(CapabilityProviderException.class)
                .extracting("errorCode")
                .isEqualTo(CapabilityProviderErrorCode.BIND_NOT_FOUND.code());
    }

    @Test
    void shouldRefreshExpiredAccessTokenAndEncryptRefreshToken() {
        CapabilityProviderConfigRegistry registry = new CapabilityProviderConfigRegistry(buildProperties());
        InMemoryCapabilityUserBindingStore bindingStore = new InMemoryCapabilityUserBindingStore();
        RecordingCapabilityProviderClient providerClient = new RecordingCapabilityProviderClient();
        providerClient.addResponse("/oauth/token/refresh", Map.of(
                "code", "OK",
                "data", Map.of(
                        "access_token", "new-access-token",
                        "refresh_token", "new-refresh-token",
                        "expires_in", 3600
                )));

        CapabilityUserBinding binding = new CapabilityUserBinding();
        binding.setTenantId("tenant-a");
        binding.setUserId("user-a");
        binding.setProviderCode("oa-provider");
        binding.setExternalUserId("oa-user-1001");
        binding.setAccessToken("expired-token");
        binding.setAccessTokenExpireAtEpochSeconds(Instant.now().minusSeconds(30).getEpochSecond());
        binding.setEncryptedRefreshToken(TokenCryptoSupport.encrypt("old-refresh-token", "demo-refresh-key"));
        bindingStore.save(binding);

        CapabilityProviderTokenService tokenService = new CapabilityProviderTokenService(
                registry, bindingStore, providerClient);
        String token = tokenService.resolveAccessToken("tenant-a", "user-a", "oa-provider");

        assertThat(token).isEqualTo("new-access-token");
        CapabilityUserBinding refreshedBinding = bindingStore
                .find("tenant-a", "user-a", "oa-provider")
                .orElseThrow();
        assertThat(refreshedBinding.getAccessToken()).isEqualTo("new-access-token");
        assertThat(TokenCryptoSupport.decrypt(refreshedBinding.getEncryptedRefreshToken(), "demo-refresh-key"))
                .isEqualTo("new-refresh-token");
    }

    private CapabilityRegistrationProperties buildProperties() {
        CapabilityRegistrationProperties properties = new CapabilityRegistrationProperties();
        CapabilityRegistrationProperties.ProviderRegistration providerRegistration =
                new CapabilityRegistrationProperties.ProviderRegistration();
        providerRegistration.setEnabled(true);
        providerRegistration.setProviderCode("oa-provider");
        providerRegistration.setBaseUrl("http://provider.test");
        providerRegistration.setTokenRefreshPath("/oauth/token/refresh");
        CapabilityRegistrationProperties.ProviderAuthConfig authConfig =
                new CapabilityRegistrationProperties.ProviderAuthConfig();
        authConfig.setRefreshTokenEncryptionKey("demo-refresh-key");
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

        @Override
        public Map<String, Object> postJson(CapabilityProviderConfig providerConfig, String path,
                Map<String, Object> payload, Map<String, String> headers) {
            return responsesByPath.getOrDefault(path, Map.of());
        }

        private void addResponse(String path, Map<String, Object> response) {
            responsesByPath.put(path, response);
        }

    }

}

