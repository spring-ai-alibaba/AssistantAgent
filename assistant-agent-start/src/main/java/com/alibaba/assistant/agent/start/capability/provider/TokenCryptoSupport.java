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

import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Token encryption helper.
 *
 * <p>Uses AES/GCM with key material derived from provider auth secret.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public final class TokenCryptoSupport {

	private static final String TRANSFORM = "AES/GCM/NoPadding";

	private static final int GCM_TAG_BITS = 128;

	private static final int IV_LENGTH = 12;

	private TokenCryptoSupport() {
	}

	public static String encrypt(String plainText, String secretKey) {
		if (!StringUtils.hasText(plainText)) {
			return "";
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			new SecureRandom().nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORM);
			cipher.init(Cipher.ENCRYPT_MODE, toSecretKey(secretKey), new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			byte[] packed = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, packed, 0, iv.length);
			System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
			return Base64.getEncoder().encodeToString(packed);
		}
		catch (Exception e) {
			throw new CapabilityProviderException(CapabilityProviderErrorCode.INVALID_PROVIDER_RESPONSE,
					"encrypt refresh token failed", e);
		}
	}

	public static String decrypt(String cipherText, String secretKey) {
		if (!StringUtils.hasText(cipherText)) {
			return "";
		}
		try {
			byte[] packed = Base64.getDecoder().decode(cipherText);
			if (packed.length <= IV_LENGTH) {
				return "";
			}
			byte[] iv = Arrays.copyOfRange(packed, 0, IV_LENGTH);
			byte[] encrypted = Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
			Cipher cipher = Cipher.getInstance(TRANSFORM);
			cipher.init(Cipher.DECRYPT_MODE, toSecretKey(secretKey), new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] plain = cipher.doFinal(encrypted);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			throw new CapabilityProviderException(CapabilityProviderErrorCode.INVALID_PROVIDER_RESPONSE,
					"decrypt refresh token failed", e);
		}
	}

	private static SecretKeySpec toSecretKey(String secretKey) throws Exception {
		String keyMaterial = StringUtils.hasText(secretKey) ? secretKey : "assistant-agent-refresh-token-key";
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hashed = digest.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
		byte[] key = Arrays.copyOf(hashed, 16);
		return new SecretKeySpec(key, "AES");
	}

}

