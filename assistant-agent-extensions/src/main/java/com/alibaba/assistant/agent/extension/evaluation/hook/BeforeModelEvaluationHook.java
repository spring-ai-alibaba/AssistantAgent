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
package com.alibaba.assistant.agent.extension.evaluation.hook;

import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhaseUtils;
import com.alibaba.assistant.agent.extension.evaluation.config.CodeactEvaluationContextFactory;
import com.alibaba.assistant.agent.extension.evaluation.store.OverAllStateEvaluationResultStore;
import com.alibaba.assistant.agent.evaluation.EvaluationService;
import com.alibaba.assistant.agent.evaluation.model.EvaluationContext;
import com.alibaba.assistant.agent.evaluation.model.EvaluationSuite;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Prioritized;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * BEFORE_MODEL 阶段的评估 Hook 抽象基类
 * 在模型调用前进行评估，统一处理模型输入增强与评估
 * 
 * <p>子类需要：
 * <ul>
 *     <li>使用 {@code @HookPhases} 注解指定适用的 Agent 阶段（基类自动读取）</li>
 *     <li>实现 {@link #createEvaluationContext(OverAllState, RunnableConfig)} 方法创建评估上下文</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @see ReactBeforeModelEvaluationHook
 * @see CodeactBeforeModelEvaluationHook
 */
@HookPositions(HookPosition.BEFORE_MODEL)
public abstract class BeforeModelEvaluationHook extends ModelHook implements Prioritized {

	private static final Logger log = LoggerFactory.getLogger(BeforeModelEvaluationHook.class);

	/**
	 * 默认的 order 值，设置为较小值以确保评估 Hook 在 PromptContributor Hook 之前执行
	 */
	protected static final int DEFAULT_ORDER = 10;

	private final EvaluationService evaluationService;
	private final CodeactEvaluationContextFactory contextFactory;
	private final String suiteId;
	private final int order;

	protected BeforeModelEvaluationHook(
			EvaluationService evaluationService,
			CodeactEvaluationContextFactory contextFactory,
			String suiteId) {
		this(evaluationService, contextFactory, suiteId, DEFAULT_ORDER);
	}

	protected BeforeModelEvaluationHook(
			EvaluationService evaluationService,
			CodeactEvaluationContextFactory contextFactory,
			String suiteId,
			int order) {
		this.evaluationService = evaluationService;
		this.contextFactory = contextFactory;
		this.suiteId = suiteId;
		this.order = order;
	}

	/**
	 * 创建评估上下文
	 * 
	 * @param state 当前状态
	 * @param config 运行配置
	 * @return 评估上下文
	 */
	protected abstract EvaluationContext createEvaluationContext(OverAllState state, RunnableConfig config);

	/**
	 * 获取 contextFactory，供子类使用
	 */
	protected CodeactEvaluationContextFactory getContextFactory() {
		return contextFactory;
	}

	@Override
	public int getOrder() {
		return order;
	}

	@Override
	public String getName() {
		AgentPhase[] phases = HookPhaseUtils.getHookPhases(this);
		String phaseName = phases.length > 0 ? phases[0].name() : "UNKNOWN";
		return "BeforeModelEvaluationHook-" + phaseName;
	}

	@Override
	public List<JumpTo> canJumpTo() {
		return List.of();
	}

	@Override
	public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
		String hookName = getName();
		log.info("{}#beforeModel - reason=开始模型输入评估", hookName);

		try {
			// 构造评估上下文（由子类实现）
			EvaluationContext context = createEvaluationContext(state, config);

			// 加载 Suite
			EvaluationSuite suite = evaluationService.loadSuite(suiteId);

			if (suite == null) {
				log.warn("{}#beforeModel - reason=未找到评估套件, suiteId={}", hookName, suiteId);
				return CompletableFuture.completedFuture(Map.of());
			}

			// 执行评估并存储到 OverAllState
			return evaluationService.evaluateAsync(suite, context)
					.thenApply(result -> {
						log.info("{}#beforeModel - reason=评估完成", hookName);

						// 使用 OverAllStateEvaluationResultStore 写入状态
						OverAllStateEvaluationResultStore store = new OverAllStateEvaluationResultStore(state);
						return store.createUpdateMap(suiteId, result);
					});

		} catch (Exception e) {
			log.error("{}#beforeModel - reason=评估失败", hookName, e);
			return CompletableFuture.completedFuture(Map.of());
		}
	}
}
