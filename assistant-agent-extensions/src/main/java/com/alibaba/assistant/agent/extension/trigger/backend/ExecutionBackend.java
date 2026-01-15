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

package com.alibaba.assistant.agent.extension.trigger.backend;

import com.alibaba.assistant.agent.extension.trigger.executor.TriggerExecutionCallback;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;

/**
 * 触发器执行后端接口
 * 负责将触发器映射到实际的调度任务
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ExecutionBackend {

	/**
	 * 注册调度任务
	 * @param definition 触发器定义
	 * @return 后端任务ID
	 */
	String schedule(TriggerDefinition definition);

	/**
	 * 取消调度任务
	 * @param backendTaskId 后端任务ID
	 */
	void cancel(String backendTaskId);

	/**
	 * 刷新调度任务（可选，用于修改触发器配置）
	 * @param backendTaskId 后端任务ID
	 * @param newDefinition 新的触发器定义
	 */
	default void refresh(String backendTaskId, TriggerDefinition newDefinition) {
		cancel(backendTaskId);
		schedule(newDefinition);
	}

	/**
	 * 检查任务是否正在运行
	 * @param backendTaskId 后端任务ID
	 * @return true表示正在运行
	 */
	boolean isRunning(String backendTaskId);

	/**
	 * 设置执行回调
	 * 用于在调度触发时执行实际的触发器逻辑
	 *
	 * @param callback 执行回调
	 */
	default void setExecutionCallback(TriggerExecutionCallback callback) {
		// 默认空实现，子类可覆盖
	}

}

