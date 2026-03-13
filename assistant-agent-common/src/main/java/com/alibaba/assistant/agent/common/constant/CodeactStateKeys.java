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
	 * 注入的全部 codeact 工具名称列表
	 *
	 * <p>类型：List&lt;String&gt;
	 * <p>由 CodeactToolsStateInitHook 在 beforeAgent 阶段注入
	 * <p>只存储工具名称，避免序列化 CodeactTool 对象（其中包含不可序列化的组件）
	 * <p>完整的工具对象应通过 CodeactToolRegistry 获取
	 * 
	 * @since 1.0.1
	 */
	public static final String CODEACT_TOOL_NAMES = "codeact_tool_names";

	/**
	 * 注入的全部 codeact 工具元数据列表
	 *
	 * <p>类型：List&lt;Map&lt;String, Object&gt;&gt;
	 * <p>由 CodeactToolsStateInitHook 在 beforeAgent 阶段注入
	 * <p>每个 Map 包含工具的可序列化元数据：
	 * <ul>
	 *   <li>name - 工具名称 (String)</li>
	 *   <li>description - 工具描述 (String)</li>
	 *   <li>parameters - 参数签名 (String)</li>
	 *   <li>targetClassName - 目标类名 (String, 可选)</li>
	 *   <li>alwaysAvailable - 是否始终可用 (Boolean, 可选)</li>
	 * </ul>
	 * <p>用于评估阶段的工具筛选，替代直接存储 CodeactTool 对象
	 * 
	 * @since 1.0.1
	 */
	public static final String CODEACT_TOOL_METADATA_LIST = "codeact_tool_metadata_list";

	/**
	 * 注入的全部 codeact 工具列表
	 *
	 * <p>类型：List&lt;CodeactTool&gt;
	 * <p>由 CodeGeneratorSubAgent.init_context 节点注入
	 * <p>上层应用可读取此列表进行评估
	 * 
	 * @deprecated 由于 CodeactTool 包含不可序列化的组件，不应将其写入 State。
	 *             请使用 {@link #CODEACT_TOOL_NAMES} 存储工具名称，并通过 CodeactToolRegistry 获取完整工具对象。
	 */
	@Deprecated
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

	// ==================== Hook 通信 ====================

	/**
	 * CodeGeneratorNode 执行时传入的 messages 列表（含 beforeModel Hook 注入的内容）
	 *
	 * <p>类型：List&lt;org.springframework.ai.chat.messages.Message&gt;
	 * <p>由 CodeGeneratorNode 在调用模型后写入，供 afterModel Hook 读取与审计。
	 * <p>写端：CodeGeneratorNode#apply（key = "codeact_node_messages"）
	 * <p>读端：afterModel Hook（如需访问本次代码生成的完整 messages 上下文）
	 */
	public static final String CODEACT_NODE_MESSAGES = "codeact_node_messages";

	// ==================== Session级别代码存储 ====================

	/**
	 * Session级别生成的代码Map
	 *
	 * <p>类型：Map&lt;String, GeneratedCode&gt;
	 * <p>Key为函数名，Value为GeneratedCode对象
	 * <p>存储在OverAllState中，会随checkpoint持久化
	 * <p>与全局CodeContext中的代码合并时，session维度的代码优先
	 */
	public static final String SESSION_GENERATED_CODES = "session_generated_codes";
}

