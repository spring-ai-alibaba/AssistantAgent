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
	 * Key for storing user ID (for Store namespace)
	 * Type: String
	 */
	public static final String USER_ID = "user_id";

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
	 * 编程语言
	 *
	 * <p>类型：String
	 * <p>示例："python", "java"
	 * <p>由 CodeGeneratorSubAgent.init_context 节点注入
	 */
	public static final String LANGUAGE = "language";

	// ==================== Experience progressive disclosure ====================

	/**
	 * 首轮预取使用的 disclosure query。
	 *
	 * <p>类型：String
	 */
	public static final String EXPERIENCE_PREFETCH_QUERY = "experience_prefetch_query";

	/**
	 * 首轮预取状态。
	 *
	 * <p>类型：String
	 * <p>取值：NOT_RUN / SKIPPED / COMPLETED
	 */
	public static final String EXPERIENCE_PREFETCH_STATUS = "experience_prefetch_status";

	/**
	 * 首轮预取得到的 grouped experience candidate cards。
	 *
	 * <p>类型：GroupedExperienceCandidates
	 */
	public static final String EXPERIENCE_PREFETCHED_CANDIDATES = "experience_prefetched_candidates";

	/**
	 * {@code read_exp} 返回的 detail cache。
	 *
	 * <p>类型：Map&lt;String, ReadExpResponse&gt;
	 */
	public static final String EXPERIENCE_DETAIL_CACHE = "experience_detail_cache";

	/**
	 * 已满足 DIRECT 披露条件、可直接注入 prompt 的经验内容。
	 *
	 * <p>类型：List&lt;DirectExperienceGrounding&gt;
	 */
	public static final String EXPERIENCE_DIRECT_GROUNDINGS = "experience_direct_groundings";

	/**
	 * 当前轮允许以 React 直调方式暴露的工具名集合。
	 *
	 * <p>类型：List&lt;String&gt;
	 */
	public static final String EXPERIENCE_ALLOWED_REACT_TOOL_NAMES = "experience_allowed_react_tool_names";

	/**
	 * 本 session 中已成功调用过 {@code read_exp} 的 experience id 集合；
	 * {@code read_exp_doc} 的 session gate 依赖该状态。
	 *
	 * <p>类型：List&lt;String&gt;
	 */
	public static final String EXPERIENCE_READ_EXP_IDS = "experience_read_exp_ids";

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
