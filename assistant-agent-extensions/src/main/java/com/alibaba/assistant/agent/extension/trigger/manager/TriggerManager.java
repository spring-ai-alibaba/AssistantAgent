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

package com.alibaba.assistant.agent.extension.trigger.manager;

import com.alibaba.assistant.agent.extension.trigger.backend.ExecutionBackend;
import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerExecutionRecord;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerStatus;
import com.alibaba.assistant.agent.extension.trigger.repository.TriggerExecutionLogRepository;
import com.alibaba.assistant.agent.extension.trigger.repository.TriggerRepository;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 触发器管理器
 * 提供触发器的创建、管理和查询能力
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class TriggerManager {

	private static final Logger log = LoggerFactory.getLogger(TriggerManager.class);

	@JsonIgnore
	private final TriggerRepository triggerRepository;

	@JsonIgnore
	private final TriggerExecutionLogRepository executionLogRepository;

	@JsonIgnore
	private final ExecutionBackend executionBackend;

	/**
	 * 触发器ID到后端任务ID的映射
	 */
	@JsonIgnore
	private final Map<String, String> triggerToBackendTaskMapping = new ConcurrentHashMap<>();

	public TriggerManager(TriggerRepository triggerRepository, TriggerExecutionLogRepository executionLogRepository,
			ExecutionBackend executionBackend) {
		this.triggerRepository = triggerRepository;
		this.executionLogRepository = executionLogRepository;
		this.executionBackend = executionBackend;
	}

	/**
	 * 订阅触发器（创建并激活）
	 * @param definition 触发器定义
	 * @return 触发器ID
	 */
	public String subscribe(TriggerDefinition definition) {
		log.info("TriggerManager.subscribe 创建触发器: name={}, scheduleMode={}", definition.getName(),
				definition.getScheduleMode());

		// 生成触发器ID
		if (definition.getTriggerId() == null) {
			definition.setTriggerId(generateTriggerId());
		}

		// 验证定义
		validateDefinition(definition);

		// 设置初始状态
		definition.setStatus(TriggerStatus.ACTIVE);
		definition.setCreatedAt(Instant.now());
		definition.setUpdatedAt(Instant.now());

		// 保存定义
		triggerRepository.save(definition);

		// 注册到执行后端
		try {
			String backendTaskId = executionBackend.schedule(definition);
			triggerToBackendTaskMapping.put(definition.getTriggerId(), backendTaskId);
			log.info("TriggerManager.subscribe 触发器创建成功: triggerId={}, backendTaskId={}", definition.getTriggerId(),
					backendTaskId);
		}
		catch (Exception e) {
			log.error("TriggerManager.subscribe 注册调度任务失败: triggerId={}", definition.getTriggerId(), e);
			// 回滚状态
			definition.setStatus(TriggerStatus.PENDING_ACTIVATE);
			triggerRepository.save(definition);
			throw new RuntimeException("Failed to schedule trigger: " + e.getMessage(), e);
		}

		return definition.getTriggerId();
	}

	/**
	 * 取消订阅触发器
	 * @param triggerId 触发器ID
	 */
	public void unsubscribe(String triggerId) {
		log.info("TriggerManager.unsubscribe 取消触发器: triggerId={}", triggerId);

		Optional<TriggerDefinition> definitionOpt = triggerRepository.findById(triggerId);
		if (!definitionOpt.isPresent()) {
			log.warn("TriggerManager.unsubscribe 触发器不存在: triggerId={}", triggerId);
			throw new IllegalArgumentException("Trigger not found: " + triggerId);
		}

		TriggerDefinition definition = definitionOpt.get();

		// 取消后端任务
		String backendTaskId = triggerToBackendTaskMapping.remove(triggerId);
		if (backendTaskId != null) {
			try {
				executionBackend.cancel(backendTaskId);
				log.info("TriggerManager.unsubscribe 后端任务已取消: backendTaskId={}", backendTaskId);
			}
			catch (Exception e) {
				log.error("TriggerManager.unsubscribe 取消后端任务失败: backendTaskId={}", backendTaskId, e);
			}
		}

		// 更新状态
		triggerRepository.updateStatus(triggerId, TriggerStatus.CANCELED);
		log.info("TriggerManager.unsubscribe 触发器已取消: triggerId={}", triggerId);
	}

	/**
	 * 暂停触发器
	 * @param triggerId 触发器ID
	 */
	public void pause(String triggerId) {
		log.info("TriggerManager.pause 暂停触发器: triggerId={}", triggerId);

		String backendTaskId = triggerToBackendTaskMapping.get(triggerId);
		if (backendTaskId != null) {
			executionBackend.cancel(backendTaskId);
		}

		triggerRepository.updateStatus(triggerId, TriggerStatus.PAUSED);
		log.info("TriggerManager.pause 触发器已暂停: triggerId={}", triggerId);
	}

	/**
	 * 恢复触发器
	 * @param triggerId 触发器ID
	 */
	public void resume(String triggerId) {
		log.info("TriggerManager.resume 恢复触发器: triggerId={}", triggerId);

		Optional<TriggerDefinition> definitionOpt = triggerRepository.findById(triggerId);
		if (!definitionOpt.isPresent()) {
			throw new IllegalArgumentException("Trigger not found: " + triggerId);
		}

		TriggerDefinition definition = definitionOpt.get();
		if (definition.getStatus() != TriggerStatus.PAUSED) {
			throw new IllegalStateException("Trigger is not paused: " + triggerId);
		}

		// 重新注册到执行后端
		String backendTaskId = executionBackend.schedule(definition);
		triggerToBackendTaskMapping.put(triggerId, backendTaskId);

		triggerRepository.updateStatus(triggerId, TriggerStatus.ACTIVE);
		log.info("TriggerManager.resume 触发器已恢复: triggerId={}, backendTaskId={}", triggerId, backendTaskId);
	}

	/**
	 * 获取触发器详情
	 * @param triggerId 触发器ID
	 * @return 触发器定义
	 */
	public Optional<TriggerDefinition> getDetail(String triggerId) {
		log.debug("TriggerManager.getDetail 查询触发器详情: triggerId={}", triggerId);
		return triggerRepository.findById(triggerId);
	}

	/**
	 * 查询触发器列表
	 * @param sourceType 来源类型
	 * @param sourceId 来源ID
	 * @return 触发器列表
	 */
	public List<TriggerDefinition> list(SourceType sourceType, String sourceId) {
		log.debug("TriggerManager.list 查询触发器列表: sourceType={}, sourceId={}", sourceType, sourceId);
		return triggerRepository.findBySource(sourceType, sourceId);
	}

	/**
	 * 查询所有触发器
	 * @return 所有触发器列表
	 */
	public List<TriggerDefinition> listAll() {
		log.debug("TriggerManager.listAll 查询所有触发器");
		return triggerRepository.findAll();
	}

	/**
	 * 查询触发器执行历史
	 * @param triggerId 触发器ID
	 * @param limit 最大返回数量
	 * @return 执行记录列表
	 */
	public List<TriggerExecutionRecord> getExecutionHistory(String triggerId, int limit) {
		log.debug("TriggerManager.getExecutionHistory 查询执行历史: triggerId={}, limit={}", triggerId, limit);
		return executionLogRepository.listByTrigger(triggerId, limit);
	}

	/**
	 * 验证触发器定义的合法性
	 * @param definition 触发器定义
	 */
	private void validateDefinition(TriggerDefinition definition) {
		if (definition.getScheduleMode() == null) {
			throw new IllegalArgumentException("scheduleMode is required");
		}
		if (definition.getScheduleValue() == null || definition.getScheduleValue().isEmpty()) {
			throw new IllegalArgumentException("scheduleValue is required");
		}
		if (definition.getExecuteFunction() == null || definition.getExecuteFunction().isEmpty()) {
			throw new IllegalArgumentException("executeFunction is required");
		}

		// TODO: 添加更多验证逻辑（如Cron表达式格式验证等）
	}

	private String generateTriggerId() {
		return "trigger_" + UUID.randomUUID().toString().replace("-", "");
	}

}

