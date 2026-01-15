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

package com.alibaba.assistant.agent.extension.trigger.executor;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.core.context.CodeContext;
import com.alibaba.assistant.agent.core.executor.GraalCodeExecutor;
import com.alibaba.assistant.agent.core.executor.RuntimeEnvironmentManager;
import com.alibaba.assistant.agent.core.executor.python.PythonEnvironmentManager;
import com.alibaba.assistant.agent.core.model.ExecutionRecord;
import com.alibaba.assistant.agent.core.model.GeneratedCode;
import com.alibaba.assistant.agent.core.tool.CodeactToolRegistry;
import com.alibaba.assistant.agent.core.tool.DefaultCodeactToolRegistry;
import com.alibaba.assistant.agent.extension.trigger.model.SessionSnapshot;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerExecutionResult;
import com.alibaba.assistant.agent.extension.trigger.repository.SessionSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 触发器执行器
 * 
 * 核心设计：复用AssistantAgent的GraalCodeExecutor实现代码执行
 * 
 * 职责：
 * 1. 恢复会话快照上下文
 * 2. 基于GraalCodeExecutor执行条件函数和动作函数
 * 3. 执行放弃条件函数
 * 
 * 应用层可以：
 * 1. 直接使用默认实现
 * 2. 继承并扩展 getAvailableTools 方法提供自定义工具
 *
 * @author canfeng
 * @since 1.0.0
 */
public class TriggerExecutor implements TriggerExecutionCallback {

	private static final Logger log = LoggerFactory.getLogger(TriggerExecutor.class);

	private final SessionSnapshotRepository snapshotRepository;

	private final RuntimeEnvironmentManager environmentManager;

	private final boolean allowIO;

	private final boolean allowNativeAccess;

	private final long executionTimeoutMs;

	public TriggerExecutor(SessionSnapshotRepository snapshotRepository) {
		this(snapshotRepository, false, false, 30000);
	}

	public TriggerExecutor(SessionSnapshotRepository snapshotRepository,
			boolean allowIO, boolean allowNativeAccess, long executionTimeoutMs) {
		this.snapshotRepository = snapshotRepository;
		this.environmentManager = new PythonEnvironmentManager();
		this.allowIO = allowIO;
		this.allowNativeAccess = allowNativeAccess;
		this.executionTimeoutMs = executionTimeoutMs;
	}

	/**
	 * 执行触发器
	 *
	 * @param definition 触发器定义
	 * @return 执行结果
	 */
	@Override
	public TriggerExecutionResult execute(TriggerDefinition definition) {
		String triggerId = definition.getTriggerId();
		log.info("TriggerExecutor execute 开始执行触发器, triggerId={}", triggerId);

		long startTime = System.currentTimeMillis();
		TriggerExecutionResult result = new TriggerExecutionResult();
		result.setTriggerId(triggerId);

		try {
			// 1. 恢复会话快照
			SessionSnapshot snapshot = restoreSnapshot(definition);
			log.debug("TriggerExecutor execute 恢复会话快照成功, snapshotId={}", 
					definition.getSessionSnapshotId());

			// 2. 构建CodeContext和CodeactToolRegistry
			CodeContext codeContext = buildCodeContext(snapshot, definition);
			CodeactToolRegistry toolRegistry = buildToolRegistry(snapshot);

			// 3. 创建GraalCodeExecutor（复用框架能力）
			GraalCodeExecutor executor = new GraalCodeExecutor(
					environmentManager,
					codeContext,
					Collections.emptyList(),  // ToolCallback列表
					null,  // OverAllState
					toolRegistry,
					allowIO,
					allowNativeAccess,
					executionTimeoutMs
			);

			// 4. 执行条件函数（如果有）
			if (hasConditionFunction(definition)) {
				boolean conditionResult = executeConditionFunction(executor, definition);
				result.setConditionPassed(conditionResult);

				if (!conditionResult) {
					log.info("TriggerExecutor execute 条件未满足, triggerId={}", triggerId);
					result.setExecutionSuccess(true);
					result.setExecutionTime(System.currentTimeMillis() - startTime);
					return result;
				}
			}
			else {
				// 没有条件函数，默认条件满足
				result.setConditionPassed(true);
			}

			// 5. 执行动作函数
			Object actionResult = executeActionFunction(executor, definition);
			result.setExecutionResult(actionResult);
			result.setExecutionSuccess(true);

			// 6. 执行放弃条件函数（如果有）
			if (hasAbandonFunction(definition)) {
				boolean shouldAbandon = executeAbandonFunction(executor, definition);
				result.setShouldAbandon(shouldAbandon);
				log.info("TriggerExecutor execute 放弃条件判断, triggerId={}, shouldAbandon={}", 
						triggerId, shouldAbandon);
			}

			log.info("TriggerExecutor execute 触发器执行成功, triggerId={}", triggerId);

		}
		catch (Exception e) {
			log.error("TriggerExecutor execute 触发器执行失败, triggerId={}", triggerId, e);
			result.setExecutionSuccess(false);
			result.setErrorMessage(e.getMessage());
		}
		finally {
			result.setExecutionTime(System.currentTimeMillis() - startTime);
		}

		return result;
	}

	/**
	 * 恢复会话快照
	 */
	protected SessionSnapshot restoreSnapshot(TriggerDefinition definition) {
		String snapshotId = definition.getSessionSnapshotId();

		// 如果有快照ID，从存储中恢复
		if (snapshotId != null && !snapshotId.isEmpty()) {
			return snapshotRepository.findById(snapshotId)
					.orElseThrow(() -> new IllegalStateException("会话快照不存在: " + snapshotId));
		}

		// 如果没有快照ID，使用definition中的函数代码快照创建临时快照
		SessionSnapshot snapshot = new SessionSnapshot();
		snapshot.setFunctionCode(definition.getFunctionCodeSnapshot());
		return snapshot;
	}

	/**
	 * 构建CodeContext
	 * 从SessionSnapshot恢复函数代码到CodeContext
	 */
	protected CodeContext buildCodeContext(SessionSnapshot snapshot, TriggerDefinition definition) {
		CodeContext codeContext = new CodeContext(Language.PYTHON);

		// 从快照恢复函数代码
		Map<String, String> functionCode = snapshot.getFunctionCode();
		if (functionCode == null || functionCode.isEmpty()) {
			// 使用definition中的函数代码快照
			functionCode = definition.getFunctionCodeSnapshot();
		}

		if (functionCode != null) {
			for (Map.Entry<String, String> entry : functionCode.entrySet()) {
				String functionName = entry.getKey();
				String code = entry.getValue();

				GeneratedCode generatedCode = new GeneratedCode(
						functionName,
						Language.PYTHON,
						code,
						"Restored from trigger snapshot"
				);
				codeContext.registerFunction(generatedCode);

				log.debug("TriggerExecutor buildCodeContext 注册函数: functionName={}", functionName);
			}
		}

		return codeContext;
	}

	/**
	 * 构建CodeactToolRegistry
	 * 子类可以覆盖此方法提供自定义工具
	 */
	protected CodeactToolRegistry buildToolRegistry(SessionSnapshot snapshot) {
		DefaultCodeactToolRegistry registry = new DefaultCodeactToolRegistry();

		// 获取可用工具并注册
		List<CodeactTool> tools = getAvailableTools(snapshot);
		for (CodeactTool tool : tools) {
			registry.register(tool);
			log.debug("TriggerExecutor buildToolRegistry 注册工具: toolName={}", 
					tool.getToolDefinition().name());
		}

		return registry;
	}

	/**
	 * 获取可用的CodeactTool列表
	 * 
	 * 子类可以覆盖此方法提供自定义工具
	 *
	 * @param snapshot 会话快照
	 * @return 可用工具列表
	 */
	protected List<CodeactTool> getAvailableTools(SessionSnapshot snapshot) {
		// 默认返回空列表，子类可覆盖提供具体工具
		return Collections.emptyList();
	}

	/**
	 * 检查是否有条件函数
	 */
	private boolean hasConditionFunction(TriggerDefinition definition) {
		return definition.getConditionFunction() != null && !definition.getConditionFunction().isEmpty();
	}

	/**
	 * 检查是否有放弃条件函数
	 */
	private boolean hasAbandonFunction(TriggerDefinition definition) {
		return definition.getAbandonFunction() != null && !definition.getAbandonFunction().isEmpty();
	}

	/**
	 * 执行条件函数
	 */
	private boolean executeConditionFunction(GraalCodeExecutor executor, TriggerDefinition definition) {
		String functionName = definition.getConditionFunction();
		log.debug("TriggerExecutor executeConditionFunction 执行条件函数, functionName={}", functionName);

		ExecutionRecord record = executor.execute(functionName, new HashMap<>());

		if (!record.isSuccess()) {
			log.warn("TriggerExecutor executeConditionFunction 条件函数执行失败, functionName={}, error={}",
					functionName, record.getErrorMessage());
			return false;
		}

		Object result = record.getResult();
		// 结果是字符串形式的 "True" 或 "False"
		return "True".equalsIgnoreCase(String.valueOf(result)) || Boolean.TRUE.equals(result);
	}

	/**
	 * 执行动作函数
	 */
	private Object executeActionFunction(GraalCodeExecutor executor, TriggerDefinition definition) {
		String functionName = definition.getExecuteFunction();
		log.debug("TriggerExecutor executeActionFunction 执行动作函数, functionName={}", functionName);

		// 获取执行参数
		Map<String, Object> args = definition.getParameters();
		if (args == null) {
			args = new HashMap<>();
		}

		ExecutionRecord record = executor.execute(functionName, args);

		if (!record.isSuccess()) {
			throw new RuntimeException("动作函数执行失败: " + record.getErrorMessage());
		}

		return record.getResult();
	}

	/**
	 * 执行放弃条件函数
	 */
	private boolean executeAbandonFunction(GraalCodeExecutor executor, TriggerDefinition definition) {
		String functionName = definition.getAbandonFunction();
		log.debug("TriggerExecutor executeAbandonFunction 执行放弃条件函数, functionName={}", functionName);

		ExecutionRecord record = executor.execute(functionName, new HashMap<>());

		if (!record.isSuccess()) {
			log.warn("TriggerExecutor executeAbandonFunction 放弃条件函数执行失败, functionName={}, error={}",
					functionName, record.getErrorMessage());
			return false;
		}

		Object result = record.getResult();
		return "True".equalsIgnoreCase(String.valueOf(result)) || Boolean.TRUE.equals(result);
	}

}

