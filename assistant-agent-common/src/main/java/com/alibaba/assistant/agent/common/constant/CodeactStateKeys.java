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
package com.alibaba.assistant.agent.common.constant;

/**
 * Constants for OverAllState keys used by CodeactAgent.
 * These keys are used to store and retrieve data from the agent's state.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public final class CodeactStateKeys {

	private CodeactStateKeys() {
		// Utility class
	}

	/**
	 * Key for storing the list of generated codes in the current session
	 * Type: List&lt;GeneratedCode&gt;
	 */
	public static final String GENERATED_CODES = "generated_codes";

	/**
	 * Key for storing the execution history in the current session
	 * Type: List&lt;ExecutionRecord&gt;
	 */
	public static final String EXECUTION_HISTORY = "execution_history";

	/**
	 * Key for storing the current execution result
	 * Type: ExecutionRecord
	 */
	public static final String CURRENT_EXECUTION = "current_execution";

	/**
	 * Key for storing the current programming language
	 * Type: Language
	 */
	public static final String CURRENT_LANGUAGE = "current_language";

	/**
	 * Key for storing user ID (for Store namespace)
	 * Type: String
	 */
	public static final String USER_ID = "user_id";

	/**
	 * Key for storing whether initial code generation is complete
	 * Type: Boolean
	 */
	public static final String INITIAL_CODE_GEN_DONE = "initial_code_gen_done";
}

