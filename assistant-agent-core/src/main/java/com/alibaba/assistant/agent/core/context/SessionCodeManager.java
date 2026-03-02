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
package com.alibaba.assistant.agent.core.context;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.core.model.GeneratedCode;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Session级别代码管理器
 *
 * <p>负责管理session维度的生成代码，与全局CodeContext配合使用。
 * Session维度的代码存储在OverAllState中，会随checkpoint持久化。
 * 当合并代码时，session维度的代码优先级高于全局代码。
 *
 * <p>使用方式：
 * <ul>
 * <li>写代码时：调用 {@link #registerSessionFunction(OverAllState, GeneratedCode)} 存储到session</li>
 * <li>执行/生成时：调用 {@link #getMergedFunctions(OverAllState, CodeContext)} 获取合并后的代码</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public final class SessionCodeManager {

	private static final Logger logger = LoggerFactory.getLogger(SessionCodeManager.class);

	private SessionCodeManager() {
		// Utility class
	}

	/**
	 * 注册函数到Session级别存储
	 *
	 * @param state OverAllState实例
	 * @param code  生成的代码
	 */
	public static void registerSessionFunction(OverAllState state, GeneratedCode code) {
		if (state == null || code == null || code.getFunctionName() == null) {
			logger.warn("SessionCodeManager#registerSessionFunction - reason=参数无效, state={}, code={}",
					state != null, code != null);
			return;
		}

		Map<String, Object> sessionCodes = getSessionCodesMap(state);

		// 将GeneratedCode转换为可序列化的Map
		Map<String, Object> codeData = new HashMap<>();
		codeData.put("functionName", code.getFunctionName());
		codeData.put("language", code.getLanguage() != null ? code.getLanguage().name() : null);
		codeData.put("code", code.getCode());
		codeData.put("parameters", code.getParameters());
		codeData.put("originalQuery", code.getOriginalQuery());
		codeData.put("createdAt", System.currentTimeMillis());

		sessionCodes.put(code.getFunctionName(), codeData);

		// 更新state
		state.updateState(Map.of(CodeactStateKeys.SESSION_GENERATED_CODES, sessionCodes));

		logger.info("SessionCodeManager#registerSessionFunction - reason=代码已注册到session, functionName={}, sessionCodeCount={}",
				code.getFunctionName(), sessionCodes.size());
	}

	/**
	 * 从Session获取生成的代码Map
	 *
	 * @param state OverAllState实例
	 * @return 函数名到GeneratedCode的映射
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, GeneratedCode> getSessionFunctions(OverAllState state) {
		if (state == null) {
			return Collections.emptyMap();
		}

		Map<String, Object> sessionCodes = getSessionCodesMap(state);
		Map<String, GeneratedCode> result = new HashMap<>();

		for (Map.Entry<String, Object> entry : sessionCodes.entrySet()) {
			try {
				Object value = entry.getValue();
				if (value instanceof Map) {
					Map<String, Object> codeData = (Map<String, Object>) value;
					GeneratedCode code = deserializeGeneratedCode(codeData);
					if (code != null) {
						result.put(entry.getKey(), code);
					}
				}
			} catch (Exception e) {
				logger.warn("SessionCodeManager#getSessionFunctions - reason=反序列化失败, functionName={}, error={}",
						entry.getKey(), e.getMessage());
			}
		}

		return result;
	}

	/**
	 * 获取合并后的所有函数（session优先）
	 *
	 * <p>合并规则：
	 * <ul>
	 * <li>先加载全局CodeContext中的函数</li>
	 * <li>再加载session中的函数，同名函数会覆盖全局的</li>
	 * </ul>
	 *
	 * @param state       OverAllState实例
	 * @param codeContext 全局CodeContext（可以为null）
	 * @return 合并后的函数集合
	 */
	public static Collection<GeneratedCode> getMergedFunctions(OverAllState state, CodeContext codeContext) {
		Map<String, GeneratedCode> merged = new LinkedHashMap<>();

		// 1. 先加载全局函数（如果有的话，但默认为空）
		if (codeContext != null) {
			for (GeneratedCode code : codeContext.getAllFunctions()) {
				merged.put(code.getFunctionName(), code);
			}
			logger.debug("SessionCodeManager#getMergedFunctions - reason=加载全局代码, count={}",
					codeContext.getFunctionCount());
		}

		// 2. 再加载session函数，覆盖同名的全局函数
		if (state != null) {
			Map<String, GeneratedCode> sessionFunctions = getSessionFunctions(state);
			for (Map.Entry<String, GeneratedCode> entry : sessionFunctions.entrySet()) {
				boolean isOverride = merged.containsKey(entry.getKey());
				merged.put(entry.getKey(), entry.getValue());
				if (isOverride) {
					logger.debug("SessionCodeManager#getMergedFunctions - reason=session代码覆盖全局代码, functionName={}",
							entry.getKey());
				}
			}
			logger.debug("SessionCodeManager#getMergedFunctions - reason=加载session代码, count={}",
					sessionFunctions.size());
		}

		logger.info("SessionCodeManager#getMergedFunctions - reason=合并完成, totalCount={}", merged.size());
		return merged.values();
	}

	/**
	 * 检查函数是否存在（在全局或session中）
	 *
	 * @param state       OverAllState实例
	 * @param codeContext 全局CodeContext
	 * @param functionName 函数名
	 * @return 是否存在
	 */
	public static boolean hasFunction(OverAllState state, CodeContext codeContext, String functionName) {
		// 先检查session
		if (state != null) {
			Map<String, GeneratedCode> sessionFunctions = getSessionFunctions(state);
			if (sessionFunctions.containsKey(functionName)) {
				return true;
			}
		}
		// 再检查全局
		if (codeContext != null) {
			return codeContext.hasFunction(functionName);
		}
		return false;
	}

	/**
	 * 获取指定函数（session优先）
	 *
	 * @param state       OverAllState实例
	 * @param codeContext 全局CodeContext
	 * @param functionName 函数名
	 * @return 函数代码（如果存在）
	 */
	public static Optional<GeneratedCode> getFunction(OverAllState state, CodeContext codeContext, String functionName) {
		// 先检查session
		if (state != null) {
			Map<String, GeneratedCode> sessionFunctions = getSessionFunctions(state);
			GeneratedCode sessionCode = sessionFunctions.get(functionName);
			if (sessionCode != null) {
				logger.debug("SessionCodeManager#getFunction - reason=从session获取函数, functionName={}",
						functionName);
				return Optional.of(sessionCode);
			}
		}
		// 再检查全局
		if (codeContext != null) {
			Optional<GeneratedCode> globalCode = codeContext.getFunction(functionName);
			if (globalCode.isPresent()) {
				logger.debug("SessionCodeManager#getFunction - reason=从全局获取函数, functionName={}",
						functionName);
			}
			return globalCode;
		}
		return Optional.empty();
	}

	/**
	 * 清除session中的所有代码
	 *
	 * @param state OverAllState实例
	 */
	public static void clearSessionFunctions(OverAllState state) {
		if (state == null) {
			return;
		}
		state.updateState(Map.of(CodeactStateKeys.SESSION_GENERATED_CODES, new HashMap<>()));
		logger.info("SessionCodeManager#clearSessionFunctions - reason=session代码已清空");
	}

	/**
	 * 获取session代码数量
	 *
	 * @param state OverAllState实例
	 * @return 代码数量
	 */
	public static int getSessionFunctionCount(OverAllState state) {
		if (state == null) {
			return 0;
		}
		return getSessionCodesMap(state).size();
	}

	// ==================== 私有方法 ====================

	@SuppressWarnings("unchecked")
	private static Map<String, Object> getSessionCodesMap(OverAllState state) {
		Optional<Object> opt = state.value(CodeactStateKeys.SESSION_GENERATED_CODES);
		if (opt.isPresent() && opt.get() instanceof Map) {
			return new HashMap<>((Map<String, Object>) opt.get());
		}
		return new HashMap<>();
	}

	@SuppressWarnings("unchecked")
	private static GeneratedCode deserializeGeneratedCode(Map<String, Object> data) {
		try {
			String functionName = (String) data.get("functionName");
			String languageStr = (String) data.get("language");
			String code = (String) data.get("code");
			String originalQuery = (String) data.get("originalQuery");

			if (functionName == null || code == null) {
				return null;
			}

			com.alibaba.assistant.agent.common.enums.Language language = null;
			if (languageStr != null) {
				try {
					language = com.alibaba.assistant.agent.common.enums.Language.valueOf(languageStr);
				} catch (IllegalArgumentException e) {
					// 默认使用PYTHON
					language = com.alibaba.assistant.agent.common.enums.Language.PYTHON;
				}
			}

			GeneratedCode generatedCode = new GeneratedCode(functionName, language, code, originalQuery);

			Object params = data.get("parameters");
			if (params instanceof List) {
				generatedCode.setParameters(new ArrayList<>((List<String>) params));
			}

			return generatedCode;
		} catch (Exception e) {
			logger.warn("SessionCodeManager#deserializeGeneratedCode - reason=反序列化失败, error={}", e.getMessage());
			return null;
		}
	}
}

