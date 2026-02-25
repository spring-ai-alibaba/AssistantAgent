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
 * Standard provider error codes.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public enum CapabilityProviderErrorCode {

	BIND_NOT_FOUND("BIND_NOT_FOUND"),

	PROVIDER_NOT_FOUND("PROVIDER_NOT_FOUND"),

	TOKEN_EXCHANGE_FAILED("TOKEN_EXCHANGE_FAILED"),

	TOKEN_REFRESH_FAILED("TOKEN_REFRESH_FAILED"),

	PROVIDER_CALL_FAILED("PROVIDER_CALL_FAILED"),

	INVALID_PROVIDER_RESPONSE("INVALID_PROVIDER_RESPONSE"),

	INVOCATION_CONTEXT_MISSING("INVOCATION_CONTEXT_MISSING");

	private final String code;

	CapabilityProviderErrorCode(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

}

