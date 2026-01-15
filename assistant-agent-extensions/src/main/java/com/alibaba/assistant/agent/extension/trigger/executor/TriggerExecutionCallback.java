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

import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerExecutionResult;

/**
 * 触发器执行回调接口
 * 用于解耦调度后端和执行逻辑
 *
 * @author canfeng
 * @since 1.0.0
 */
@FunctionalInterface
public interface TriggerExecutionCallback {

	/**
	 * 执行触发器
	 *
	 * @param definition 触发器定义
	 * @return 执行结果
	 */
	TriggerExecutionResult execute(TriggerDefinition definition);

}

