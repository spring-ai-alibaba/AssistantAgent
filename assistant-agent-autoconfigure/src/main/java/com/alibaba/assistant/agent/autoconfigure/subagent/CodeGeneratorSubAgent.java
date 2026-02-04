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
package com.alibaba.assistant.agent.autoconfigure.subagent;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.hook.AgentPhase;
import com.alibaba.assistant.agent.common.hook.HookPhaseUtils;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.autoconfigure.subagent.node.CodeGeneratorNode;
import com.alibaba.assistant.agent.core.tool.schema.ReturnSchemaRegistry;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.internal.node.Node;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CodeGeneratorSubAgent - 代码生成子Agent（参考ReactAgent，但基于BaseAgent）
 *
 * <p>实现代码生成的业务逻辑，图结构为：START -> init_context -> code_gen -> END
 * <p>不需要多轮推理，只负责一次性代码生成
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class CodeGeneratorSubAgent extends CodeactSubAgent {

	private final CodeGeneratorNode codeGenNode;

	public CodeGeneratorSubAgent(CodeGeneratorNode codeGenNode, Builder builder) {
		super(builder.name,
			  builder.description,
			  builder.hooks,
			  builder.modelInterceptors,
			  "generated_code");

		this.codeGenNode = codeGenNode;

		// 设置拦截器到节点（参考ReactAgent的做法）
		if (this.modelInterceptors != null && !this.modelInterceptors.isEmpty()) {
			this.codeGenNode.setModelInterceptors(this.modelInterceptors);
		}
	}

	@Override
	protected StateGraph initGraph() throws GraphStateException {
		StateGraph graph = new StateGraph();

		// ========== 初始化 Hooks ==========
		List<? extends Hook> currentHooks = this.hooks;
		if (currentHooks == null) {
			currentHooks = new ArrayList<>();
		}

		// 使用 HookPhaseUtils 过滤出 CODEACT 阶段适用的 Hooks
		// 这样可以自动根据 @HookPhases 注解过滤，只保留适用于 CodeAct 阶段的 Hook
		@SuppressWarnings("unchecked")
		List<Hook> phaseFilteredHooks = HookPhaseUtils.filterByPhase((List<Hook>) currentHooks, AgentPhase.CODEACT);
		logger.info("CodeGeneratorSubAgent#initGraph 按阶段过滤 Hooks: total={}, codeactPhase={}",
				currentHooks.size(), phaseFilteredHooks.size());

		// 验证 Hook 唯一性并设置 Agent 信息
		// 注意：CodeGeneratorSubAgent 不是 ReactAgent，因此只设置 agentName，不设置 agent 引用
		Set<String> hookNames = new HashSet<>();
		for (Hook hook : phaseFilteredHooks) {
			if (!hookNames.add(Hook.getFullHookName(hook))) {
				throw new IllegalArgumentException("Duplicate hook instances found: " + Hook.getFullHookName(hook));
			}
			hook.setAgentName(this.name);
			// 跳过 setAgent 调用，因为 Hook.setAgent 期望 ReactAgent 类型
			// 而 CodeGeneratorSubAgent 继承自 BaseAgent，不是 ReactAgent
		}

		// 按位置分类 Hooks（CodeGeneratorSubAgent 只处理 BEFORE_MODEL 和 AFTER_MODEL）
		List<Hook> beforeModelHooks = filterHooksByPosition(phaseFilteredHooks, HookPosition.BEFORE_MODEL);
		List<Hook> afterModelHooks = filterHooksByPosition(phaseFilteredHooks, HookPosition.AFTER_MODEL);

		logger.info("CodeGeneratorSubAgent#initGraph Hooks分类完成: beforeModel={}, afterModel={}",
				beforeModelHooks.size(), afterModelHooks.size());

		// ========== 添加节点 ==========

		// 添加初始化节点：将 codeactTools 和 language 注入到 state 中
		graph.addNode("init_context", AsyncNodeActionWithConfig.node_async(
				(state, config) -> {
					Map<String, Object> updates = new HashMap<>();
					
					// 注入 codeactTools
					List<CodeactTool> tools = codeGenNode.getCodeactTools();
					if (tools != null && !tools.isEmpty()) {
						updates.put("codeact_tools", tools);
						logger.debug("CodeGeneratorSubAgent#init_context 注入 codeact_tools，数量: {}", tools.size());
					}
					
					// 注入 language（供 BeforeModelEvaluationHook 使用）
					Language language = codeGenNode.getLanguage();
					if (language != null) {
						updates.put("language", language.name().toLowerCase());
						logger.debug("CodeGeneratorSubAgent#init_context 注入 language: {}", language.name());
					}
					
					return updates;
				}
		));

		// 添加 beforeModel Hook 节点
		for (Hook hook : beforeModelHooks) {
			String nodeName = Hook.getFullHookName(hook) + ".beforeModel";
			if (hook instanceof ModelHook modelHook) {
				graph.addNode(nodeName, modelHook::beforeModel);
				logger.debug("CodeGeneratorSubAgent#initGraph 添加 beforeModel Hook 节点: {}", nodeName);
			} else if (hook instanceof MessagesModelHook messagesModelHook) {
				graph.addNode(nodeName, MessagesModelHook.beforeModelAction(messagesModelHook));
				logger.debug("CodeGeneratorSubAgent#initGraph 添加 beforeModel MessagesHook 节点: {}", nodeName);
			}
		}

		// 添加代码生成节点 (wrap NodeActionWithConfig as AsyncNodeActionWithConfig)
		graph.addNode("code_gen", AsyncNodeActionWithConfig.node_async(codeGenNode));

		// 添加 afterModel Hook 节点
		for (Hook hook : afterModelHooks) {
			String nodeName = Hook.getFullHookName(hook) + ".afterModel";
			if (hook instanceof ModelHook modelHook) {
				graph.addNode(nodeName, modelHook::afterModel);
				logger.debug("CodeGeneratorSubAgent#initGraph 添加 afterModel Hook 节点: {}", nodeName);
			} else if (hook instanceof MessagesModelHook messagesModelHook) {
				graph.addNode(nodeName, MessagesModelHook.afterModelAction(messagesModelHook));
				logger.debug("CodeGeneratorSubAgent#initGraph 添加 afterModel MessagesHook 节点: {}", nodeName);
			}
		}

		// ========== 设置边（流程连接） ==========
		// 流程：START -> init_context -> [beforeModel hooks] -> code_gen -> [afterModel hooks] -> END

		graph.addEdge(StateGraph.START, "init_context");

		// 确定 init_context 之后的节点
		String nodeAfterInit = determineNextNode(beforeModelHooks, "code_gen", ".beforeModel");
		graph.addEdge("init_context", nodeAfterInit);

		// 连接 beforeModel hooks 链
		connectHookChain(graph, beforeModelHooks, ".beforeModel", "code_gen");

		// 确定 code_gen 之后的节点
		String nodeAfterCodeGen = determineNextNode(afterModelHooks, StateGraph.END, ".afterModel");
		graph.addEdge("code_gen", nodeAfterCodeGen);

		// 连接 afterModel hooks 链
		connectHookChain(graph, afterModelHooks, ".afterModel", StateGraph.END);

		logger.info("CodeGeneratorSubAgent#initGraph 图结构初始化完成（含 {} 个 Hook 节点）",
				beforeModelHooks.size() + afterModelHooks.size());
		return graph;
	}

	/**
	 * 按位置过滤 Hooks，并按优先级排序
	 * 参考 ReactAgent.filterHooksByPosition 实现
	 */
	private static List<Hook> filterHooksByPosition(List<? extends Hook> hooks, HookPosition position) {
		List<Hook> filtered = hooks.stream()
				.filter(hook -> {
					HookPosition[] positions = hook.getHookPositions();
					return Arrays.asList(positions).contains(position);
				})
				.collect(Collectors.toList());

		// 分离实现了 Ordered 接口的 hooks
		List<Hook> orderedHooks = new ArrayList<>();
		List<Hook> nonOrderedHooks = new ArrayList<>();

		for (Hook hook : filtered) {
			if (hook instanceof Ordered) {
				orderedHooks.add(hook);
			} else {
				nonOrderedHooks.add(hook);
			}
		}

		// 按 order 值排序
		orderedHooks.sort((h1, h2) -> Integer.compare(
				((Ordered) h1).getOrder(),
				((Ordered) h2).getOrder()));

		// 合并：有序的在前，无序的保持原顺序
		List<Hook> result = new ArrayList<>(orderedHooks);
		result.addAll(nonOrderedHooks);
		return result;
	}

	/**
	 * 确定下一个节点名称
	 * 如果 hooks 列表非空，返回第一个 hook 的节点名；否则返回默认节点
	 */
	private String determineNextNode(List<Hook> hooks, String defaultNode, String suffix) {
		if (hooks == null || hooks.isEmpty()) {
			return defaultNode;
		}
		return Hook.getFullHookName(hooks.get(0)) + suffix;
	}

	/**
	 * 连接 Hook 链
	 * 将 hooks 列表中的节点依次连接，最后一个连接到 targetNode
	 */
	private void connectHookChain(StateGraph graph, List<Hook> hooks, String suffix, String targetNode) 
			throws GraphStateException {
		if (hooks == null || hooks.isEmpty()) {
			return;
		}

		for (int i = 0; i < hooks.size(); i++) {
			String currentNode = Hook.getFullHookName(hooks.get(i)) + suffix;
			String nextNode;

			if (i < hooks.size() - 1) {
				// 连接到下一个 hook
				nextNode = Hook.getFullHookName(hooks.get(i + 1)) + suffix;
			} else {
				// 最后一个 hook 连接到目标节点
				nextNode = targetNode;
			}

			graph.addEdge(currentNode, nextNode);
			logger.debug("CodeGeneratorSubAgent#connectHookChain 添加边: {} -> {}", currentNode, nextNode);
		}
	}

    @Override
    public Node asNode(boolean includeContents, boolean returnReasoningContents) {
        throw new UnsupportedOperationException("CodeGeneratorSubAgent does not support asNode()");
    }

    /**
	 * Builder for CodeGeneratorSubAgent（参考ReactAgent.Builder）
	 */
	public static class Builder {
		private String name = "code-generator";
		private String description = "Generate function code based on requirements";
		private ChatModel chatModel;
		private Language language = Language.PYTHON;
		private List<CodeactTool> codeactTools;
		private List<? extends Hook> hooks;
		private List<ModelInterceptor> modelInterceptors;
		private boolean isCondition = false;
		private String customSystemPrompt;
		private ReturnSchemaRegistry returnSchemaRegistry;

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder chatModel(ChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

		public Builder language(Language language) {
			this.language = language;
			return this;
		}

		public Builder codeactTools(List<CodeactTool> codeactTools) {
			this.codeactTools = codeactTools;
			return this;
		}

		public Builder hooks(List<? extends Hook> hooks) {
			this.hooks = hooks;
			return this;
		}

		public Builder modelInterceptors(List<ModelInterceptor> modelInterceptors) {
			this.modelInterceptors = modelInterceptors;
			return this;
		}

		public Builder isCondition(boolean isCondition) {
			this.isCondition = isCondition;
			return this;
		}

		public Builder returnSchemaRegistry(ReturnSchemaRegistry returnSchemaRegistry) {
			this.returnSchemaRegistry = returnSchemaRegistry;
			return this;
		}

		public Builder customSystemPrompt(String customSystemPrompt) {
			this.customSystemPrompt = customSystemPrompt;
			return this;
		}

		public CodeGeneratorSubAgent build() {
			if (chatModel == null) {
				throw new IllegalArgumentException("ChatModel is required");
			}

			// 创建CodeGeneratorNode，传入 returnSchemaRegistry
			CodeGeneratorNode codeGenNode = new CodeGeneratorNode(
					chatModel,
					language,
					codeactTools,
					modelInterceptors,
					"generated_code",
					isCondition,
					customSystemPrompt,
					returnSchemaRegistry
			);

			return new CodeGeneratorSubAgent(codeGenNode, this);
		}
	}

	/**
	 * Create a builder
	 */
	public static Builder builder() {
		return new Builder();
	}
}

