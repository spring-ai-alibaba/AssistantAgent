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

package com.alibaba.assistant.agent.extension.learning.extractor;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.learning.model.LearningContext;
import com.alibaba.assistant.agent.extension.learning.spi.LearningExtractor;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.*;

/**
 * 经验学习提取器（LLM智能版）
 * <p>使用大模型智能分析和提取经验，而不是简单粗暴地直接保存原始MessageList
 *
 * <p>核心能力：
 * <ul>
 *   <li>LLM判断：是否值得学习（避免噪音和无意义对话）</li>
 *   <li>LLM提取：从冗长对话中提取关键信息</li>
 *   <li>LLM总结：生成简洁、可复用的经验内容</li>
 *   <li>结构化输出：标准的Experience对象</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExperienceLearningExtractor implements LearningExtractor<Experience> {

	private static final Logger log = LoggerFactory.getLogger(ExperienceLearningExtractor.class);

	private static final String LEARNING_TYPE = "experience";

	/**
	 * LLM模型（必需）
	 */
	private final ChatModel chatModel;

	/**
	 * JSON处理器
	 */
	private final ObjectMapper objectMapper;

	/**
	 * 构造函数
	 * @param chatModel LLM模型，用于智能判断和提取
	 */
	public ExperienceLearningExtractor(ChatModel chatModel) {
		if (chatModel == null) {
			throw new IllegalArgumentException("ChatModel is required for ExperienceLearningExtractor");
		}
		this.chatModel = chatModel;
		this.objectMapper = new ObjectMapper();

		log.info("ExperienceLearningExtractor#constructor - reason=LLM-based extractor initialized");
	}

	@Override
	public boolean shouldLearn(LearningContext context) {
		if (context == null) {
			log.info("ExperienceLearningExtractor#shouldLearn - reason=context is null, result=false");
			return false;
		}

		Object stateObj = context.getOverAllState();
		if (stateObj == null) {
			log.info("ExperienceLearningExtractor#shouldLearn - reason=overall state is null, result=false");
			return false;
		}

		if (!(stateObj instanceof OverAllState)) {
			log.info("ExperienceLearningExtractor#shouldLearn - reason=state is not OverAllState type, result=false");
			return false;
		}

		OverAllState state = (OverAllState) stateObj;

		// 基础检查：是否有可学习内容
		boolean hasGeneratedCode = state.value(CodeactStateKeys.GENERATED_CODES).isPresent();
		boolean hasToolCalls = context.getToolCallRecords() != null && !context.getToolCallRecords().isEmpty();
		boolean hasExecutionHistory = state.value(CodeactStateKeys.EXECUTION_HISTORY).isPresent();
		boolean hasConversation = context.getConversationHistory() != null && !context.getConversationHistory().isEmpty();

		if (!hasGeneratedCode && !hasToolCalls && !hasExecutionHistory && !hasConversation) {
			log.info("ExperienceLearningExtractor#shouldLearn - reason=no learnable content found, result=false");
			return false;
		}

		// LLM智能判断：是否值得学习
		try {
			boolean worthLearning = llmJudgeWorthLearning(context, state);
			log.info("ExperienceLearningExtractor#shouldLearn - reason=llm judgment completed, worthLearning={}, result={}",
					worthLearning, worthLearning);
			return worthLearning;
		} catch (Exception e) {
			log.error("ExperienceLearningExtractor#shouldLearn - reason=llm judgment failed, skip learning", e);
			return false;
		}
	}

	@Override
	public List<Experience> extract(LearningContext context) {
		log.info("ExperienceLearningExtractor#extract - reason=starting LLM-based experience extraction");

		try {
			List<Experience> experiences = llmExtractExperiences(context);

			if (experiences.isEmpty()) {
				log.warn("ExperienceLearningExtractor#extract - reason=llm extraction returned empty");
			} else {
				log.info("ExperienceLearningExtractor#extract - reason=llm extraction completed successfully, count={}",
						experiences.size());
			}

			return experiences;

		} catch (Exception e) {
			log.error("ExperienceLearningExtractor#extract - reason=llm extraction failed", e);
			return new ArrayList<>();
		}
	}

	@Override
	public String getSupportedLearningType() {
		return LEARNING_TYPE;
	}

	@Override
	public Class<Experience> getRecordType() {
		return Experience.class;
	}

	// ==================== LLM智能提取方法 ====================

	/**
	 * LLM判断：是否值得学习
	 */
	private boolean llmJudgeWorthLearning(LearningContext context, OverAllState state) {
		String systemPrompt = """
				你是一个智能学习系统的判断器。分析Agent执行过程，判断是否值得提取为经验。
				
				判断标准：
				✅ 值得学习：成功解决问题、有效方法、可复用代码、明确需求和方案
				❌ 不值得学习：执行失败、简单问候、无实质内容、用户取消
				
				只回答YES或NO，不要解释。
				""";

		String userPrompt = buildContextSummary(context, state);

		try {
			Prompt prompt = new Prompt(List.of(
					new SystemMessage(systemPrompt),
					new UserMessage(userPrompt)
			));

			ChatResponse response = chatModel.call(prompt);
			String answer = response.getResult().getOutput().getText().trim().toUpperCase();

			log.info("ExperienceLearningExtractor#llmJudgeWorthLearning - reason=llm answered, answer={}", answer);
			return answer.contains("YES");

		} catch (Exception e) {
			log.warn("ExperienceLearningExtractor#llmJudgeWorthLearning - reason=llm call failed", e);
			throw e;
		}
	}

	/**
	 * LLM提取经验
	 */
	private List<Experience> llmExtractExperiences(LearningContext context) {
		String systemPrompt = """
				你是智能学习系统的提取器。从Agent执行中提取可复用经验。
				
				提取类型：
				1. COMMON：需求理解、通用知识、解决思路、最佳实践、安全边界
				2. REACT：多步处理策略、决策流程、代码生成模式、任务编排方法
				3. TOOL：单个工具的使用前提、调用方式、适用边界、常见注意事项
				
				JSON格式输出：
				```json
				[
				  {
				    "type": "COMMON|REACT|TOOL",
				    "title": "简短标题（10字内）",
				    "summary": "核心要点（50字内）",
				    "content": "详细内容（200字内，重点可复用性）",
				    "tags": ["标签1", "标签2"]
				  }
				]
				```
				
				要求：
				- 只提取有价值、可复用的经验
				- 内容简洁、结构化，避免冗长
				- 不要包含具体对话或执行细节
				- 提炼通用模式和方法
				- 无经验返回 []
				""";

		String userPrompt = buildDetailedContext(context);

		try {
			Prompt prompt = new Prompt(List.of(
					new SystemMessage(systemPrompt),
					new UserMessage(userPrompt)
			));

			ChatResponse response = chatModel.call(prompt);
			String jsonOutput = response.getResult().getOutput().getText().trim();

			log.info("ExperienceLearningExtractor#llmExtractExperiences - reason=llm extraction completed, outputLength={}",
					jsonOutput.length());

			return parseExperiencesFromJson(jsonOutput);

		} catch (Exception e) {
			log.error("ExperienceLearningExtractor#llmExtractExperiences - reason=llm extraction failed", e);
			throw e;
		}
	}

	/**
	 * 构建上下文摘要（用于判断）
	 */
	private String buildContextSummary(LearningContext context, OverAllState state) {
		StringBuilder summary = new StringBuilder();

		// 用户输入
		state.value("input", String.class).ifPresent(input ->
				summary.append("用户输入: ").append(truncate(input, 100)).append("\n")
		);

		// 代码生成
		if (state.value(CodeactStateKeys.GENERATED_CODES).isPresent()) {
			summary.append("生成了代码\n");
		}

		// 对话历史
		if (context.getConversationHistory() != null) {
			summary.append("对话轮次: ").append(context.getConversationHistory().size()).append("\n");
		}

		// 工具调用
		if (context.getToolCallRecords() != null) {
			summary.append("使用了工具: ").append(context.getToolCallRecords().size()).append("次\n");
		}

		return summary.toString();
	}

	/**
	 * 构建详细上下文（用于提取）
	 */
	private String buildDetailedContext(LearningContext context) {
		StringBuilder details = new StringBuilder();

		Object stateObj = context.getOverAllState();
		if (stateObj instanceof OverAllState) {
			OverAllState state = (OverAllState) stateObj;

			// 用户需求
			state.value("input", String.class).ifPresent(input -> {
				details.append("## 用户需求\n");
				details.append(truncate(input, 200)).append("\n\n");
			});

			// 生成的代码
			state.value(CodeactStateKeys.GENERATED_CODES).ifPresent(codes -> {
				details.append("## 生成的代码\n");
				details.append(summarizeCodes(codes)).append("\n\n");
			});
		}

		// 对话要点（只取最后4轮）
		if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
			details.append("## 对话要点\n");
			List<Object> messages = context.getConversationHistory();
			int start = Math.max(0, messages.size() - 4);
			for (int i = start; i < messages.size(); i++) {
				String msg = messages.get(i).toString();
				details.append("- ").append(truncate(msg, 150)).append("\n");
			}
			details.append("\n");
		}

		// 工具使用
		if (context.getToolCallRecords() != null && !context.getToolCallRecords().isEmpty()) {
			details.append("## 工具使用\n");
			context.getToolCallRecords().forEach(record -> {
				details.append("- 工具: ").append(record.getToolName())
						.append(", 成功: ").append(record.isSuccess())
						.append("\n");
			});
			details.append("\n");
		}

		return details.toString();
	}

	/**
	 * 从JSON解析经验
	 */
	@SuppressWarnings("unchecked")
	private List<Experience> parseExperiencesFromJson(String jsonOutput) {
		List<Experience> experiences = new ArrayList<>();

		try {
			String json = extractJsonArray(jsonOutput);
			List<Map<String, Object>> items = objectMapper.readValue(json, List.class);

			for (Map<String, Object> item : items) {
				try {
					Experience experience = buildExperienceFromMap(item);
					experiences.add(experience);
					log.info("ExperienceLearningExtractor#parseExperiencesFromJson - reason=parsed experience, type={}, title={}",
							experience.getType(), experience.getTitle());
				} catch (Exception e) {
					log.warn("ExperienceLearningExtractor#parseExperiencesFromJson - reason=failed to parse item", e);
				}
			}

		} catch (Exception e) {
			log.error("ExperienceLearningExtractor#parseExperiencesFromJson - reason=failed to parse json", e);
			throw new RuntimeException("Failed to parse experiences from LLM output", e);
		}

		return experiences;
	}

	/**
	 * 提取JSON数组
	 */
	private String extractJsonArray(String output) {
		String cleaned = output.trim();
		if (cleaned.startsWith("```json")) {
			cleaned = cleaned.substring(7);
		} else if (cleaned.startsWith("```")) {
			cleaned = cleaned.substring(3);
		}
		if (cleaned.endsWith("```")) {
			cleaned = cleaned.substring(0, cleaned.length() - 3);
		}
		return cleaned.trim();
	}

	/**
	 * 从Map构建Experience
	 */
	@SuppressWarnings("unchecked")
	private Experience buildExperienceFromMap(Map<String, Object> item) {
		String typeStr = (String) item.get("type");
		ExperienceType type = resolveExperienceType(typeStr);

		String title = (String) item.get("title");
		String summary = (String) item.getOrDefault("summary", "");
		String content = (String) item.get("content");

		List<String> tags = item.containsKey("tags")
				? (List<String>) item.get("tags")
				: new ArrayList<>();

		tags.add("llm_generated");

		String fullContent = summary.isEmpty() ? content : summary + "\n\n" + content;

		// 使用构造函数创建Experience
		Experience experience = new Experience(type, title, fullContent);
        // 设置标签
        for (String tag : tags) {
            experience.addTag(tag);
		}

		// 设置时间
		experience.setCreatedAt(Instant.now());
		experience.setUpdatedAt(Instant.now());

		return experience;
	}

	private ExperienceType resolveExperienceType(String typeStr) {
		if (typeStr == null || typeStr.isBlank()) {
			throw new IllegalArgumentException("Experience type is required");
		}
		String normalized = typeStr.trim().toUpperCase(Locale.ROOT);
		return ExperienceType.valueOf(normalized);
	}

	/**
	 * 总结代码信息
	 */
	private String summarizeCodes(Object codes) {
		if (codes == null) {
			return "无";
		}

		if (codes instanceof List) {
			List<?> codeList = (List<?>) codes;
			if (codeList.isEmpty()) {
				return "无";
			}

			StringBuilder summary = new StringBuilder();
			for (int i = 0; i < Math.min(codeList.size(), 2); i++) {
				Object code = codeList.get(i);
				summary.append("- ").append(truncate(code.toString(), 100)).append("\n");
			}
			return summary.toString();
		}

		return truncate(codes.toString(), 200);
	}

	/**
	 * 截断字符串
	 */
	private String truncate(String text, int maxLength) {
		if (text == null || text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "...";
	}

}
