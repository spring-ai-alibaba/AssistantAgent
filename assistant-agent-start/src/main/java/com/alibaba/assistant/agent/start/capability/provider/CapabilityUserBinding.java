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

/**
 * External account binding for provider calls.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CapabilityUserBinding {

	private String tenantId;

	private String userId;

	private String providerCode;

	private String externalUserId;

	private String accessToken;

	private long accessTokenExpireAtEpochSeconds;

	private String encryptedRefreshToken;

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getProviderCode() {
		return providerCode;
	}

	public void setProviderCode(String providerCode) {
		this.providerCode = providerCode;
	}

	public String getExternalUserId() {
		return externalUserId;
	}

	public void setExternalUserId(String externalUserId) {
		this.externalUserId = externalUserId;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public long getAccessTokenExpireAtEpochSeconds() {
		return accessTokenExpireAtEpochSeconds;
	}

	public void setAccessTokenExpireAtEpochSeconds(long accessTokenExpireAtEpochSeconds) {
		this.accessTokenExpireAtEpochSeconds = accessTokenExpireAtEpochSeconds;
	}

	public String getEncryptedRefreshToken() {
		return encryptedRefreshToken;
	}

	public void setEncryptedRefreshToken(String encryptedRefreshToken) {
		this.encryptedRefreshToken = encryptedRefreshToken;
	}

}

