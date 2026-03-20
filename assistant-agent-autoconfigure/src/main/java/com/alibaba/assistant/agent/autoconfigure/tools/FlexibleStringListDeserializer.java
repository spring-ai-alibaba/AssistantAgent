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
package com.alibaba.assistant.agent.autoconfigure.tools;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Jackson deserializer that accepts both a JSON array of strings and a
 * plain string for {@code List<String>} fields.
 *
 * <p>LLMs frequently produce a bare string (e.g. {@code "\n"}, {@code "a, b"})
 * instead of a proper JSON array for the {@code parameters} field. The default
 * Jackson deserializer rejects this with a type-mismatch error. This
 * deserializer normalises both forms:
 * <ul>
 *   <li>JSON array &rarr; each non-blank element is trimmed and collected</li>
 *   <li>JSON string &rarr; split by comma, each non-blank part is trimmed and collected</li>
 *   <li>JSON null &rarr; empty list</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class FlexibleStringListDeserializer extends StdDeserializer<List<String>> {

	public FlexibleStringListDeserializer() {
		super(List.class);
	}

	@Override
	public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		JsonToken token = p.currentToken();

		if (token == JsonToken.START_ARRAY) {
			List<String> result = new ArrayList<>();
			while (p.nextToken() != JsonToken.END_ARRAY) {
				String item = p.getValueAsString();
				if (item != null) {
					item = item.trim();
					if (!item.isEmpty()) {
						result.add(item);
					}
				}
			}
			return result;
		}

		if (token == JsonToken.VALUE_STRING) {
			String str = p.getValueAsString().trim();
			if (str.isEmpty()) {
				return new ArrayList<>();
			}
			List<String> result = new ArrayList<>();
			for (String part : str.split(",")) {
				String trimmed = part.trim();
				if (!trimmed.isEmpty()) {
					result.add(trimmed);
				}
			}
			return result;
		}

		if (token == JsonToken.VALUE_NULL) {
			return new ArrayList<>();
		}

		// Fallback for any other token type
		return new ArrayList<>();
	}

	@Override
	public List<String> getNullValue(DeserializationContext ctxt) {
		return new ArrayList<>();
	}

}
