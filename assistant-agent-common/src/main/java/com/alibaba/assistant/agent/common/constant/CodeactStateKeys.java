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

	// ==================== 代码生成上下文 ====================

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

	// ==================== 工具白名单配置 ====================

	/**
	 * 可用工具名称白名单
	 * 
	 * <p>类型：List&lt;String&gt;
	 * <p>示例：["search_app", "reply_user", "get_project_info"]
	 * <p>用途：精确指定允许使用的工具，工具名称对应 CodeactTool.getName()
	 * <p>为空或不存在时：不按名称筛选
	 * 
	 * <p><b>注意</b>：这里存储的是工具的 name（CodeactTool.getName()），不是独立的 ID。
	 * 如果评估时 LLM 输出的是缩写/简短 ID，上层应用需要在写入 state 前将 ID 转换为对应的工具 name。
	 */
	public static final String AVAILABLE_TOOL_NAMES = "available_tool_names";

	/**
	 * 可用工具组白名单
	 * 
	 * <p>类型：List&lt;String&gt;
	 * <p>示例：["search", "reply", "app_helper"]
	 * <p>用途：按工具组筛选，组名对应 CodeactToolMetadata.targetClassName()
	 * <p>为空或不存在时：不按组筛选
	 */
	public static final String AVAILABLE_TOOL_GROUPS = "available_tool_groups";

	/**
	 * 白名单模式
	 * 
	 * <p>类型：String
	 * <p>可选值：
	 *   - "INTERSECTION"（默认）：名称白名单和组白名单取交集
	 *   - "UNION"：名称白名单和组白名单取并集
	 *   - "NAME_ONLY"：仅使用名称白名单
	 *   - "GROUP_ONLY"：仅使用组白名单
	 * <p>为空或不存在时：默认为 INTERSECTION
	 */
	public static final String WHITELIST_MODE = "tool_whitelist_mode";

	// ==================== 工具上下文（只读） ====================

	/**
	 * 注入的全部 codeact 工具列表
	 * 
	 * <p>类型：List&lt;CodeactTool&gt;
	 * <p>由 CodeGeneratorSubAgent.init_context 节点注入
	 * <p>上层应用可读取此列表进行评估
	 */
	public static final String CODEACT_TOOLS = "codeact_tools";

	/**
	 * 筛选后的 codeact 工具列表
	 * 
	 * <p>类型：List&lt;CodeactTool&gt;
	 * <p>由 CodeGeneratorNode 筛选后写入（可选）
	 * <p>用于调试和审计
	 */
	public static final String FILTERED_CODEACT_TOOLS = "filtered_codeact_tools";

	/**
	 * 编程语言
	 * 
	 * <p>类型：String
	 * <p>示例："python", "java"
	 * <p>由 CodeGeneratorSubAgent.init_context 节点注入
	 */
	public static final String LANGUAGE = "language";
}

