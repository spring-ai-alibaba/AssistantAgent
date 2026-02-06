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
package com.alibaba.assistant.agent.evaluation.evaluator;

import com.alibaba.assistant.agent.evaluation.executor.SourcePathResolver;
import com.alibaba.assistant.agent.evaluation.model.CriterionExecutionContext;
import com.alibaba.assistant.agent.evaluation.model.CriterionResult;
import com.alibaba.assistant.agent.evaluation.model.CriterionStatus;
import com.alibaba.assistant.agent.evaluation.model.EvaluationCriterion;
import com.alibaba.assistant.agent.evaluation.model.MediaConvertible;
import com.alibaba.assistant.agent.evaluation.model.MultimodalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 支持多模态输入的LLM评估器
 * 继承自LLMBasedEvaluator，增加图片等多模态内容的处理能力
 *
 * @author Assistant Agent Team
 */
public class MultimodalLLMBasedEvaluator extends LLMBasedEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(MultimodalLLMBasedEvaluator.class);

    private final ChatModel multimodalChatModel;

    /**
     * 构造函数
     *
     * @param textModel 纯文本模型，用于普通评估
     * @param multimodalModel 多模态模型，用于处理图片等多模态输入
     * @param evaluatorId 评估器ID
     */
    public MultimodalLLMBasedEvaluator(ChatModel textModel, ChatModel multimodalModel, String evaluatorId) {
        super(textModel, evaluatorId);
        this.multimodalChatModel = multimodalModel;
    }

    @Override
    public CriterionResult evaluate(CriterionExecutionContext executionContext) {
        EvaluationCriterion criterion = executionContext.getCriterion();
        MultimodalConfig multimodalConfig = criterion.getMultimodalConfig();

        // 如果没有多模态配置，使用父类的纯文本评估
        if (multimodalConfig == null || !multimodalConfig.isEnabled()) {
            return super.evaluate(executionContext);
        }

        // 获取多模态内容
        List<Media> mediaList = extractMediaFromContext(executionContext, multimodalConfig);

        // 如果没有有效的媒体内容，降级为纯文本评估
        if (mediaList == null || mediaList.isEmpty()) {
            logger.debug("No valid media found for multimodal criterion '{}', falling back to text-only evaluation",
                    criterion.getName());
            return super.evaluate(executionContext);
        }

        // 执行多模态评估
        return evaluateWithMultimodal(executionContext, mediaList);
    }

    /**
     * 从上下文中提取媒体内容
     * 仅处理实现了 MediaConvertible 接口的对象
     */
    protected List<Media> extractMediaFromContext(CriterionExecutionContext context,
                                                   MultimodalConfig config) {
        Object sourceValue = SourcePathResolver.resolve(
                config.getSourcePath(),
                context.getInputContext(),
                context.getDependencyResults()
        );

        if (sourceValue == null) {
            return Collections.emptyList();
        }

        List<Media> mediaList = new ArrayList<>();

        // 支持 List<MediaConvertible> 类型
        if (sourceValue instanceof List<?>) {
            for (Object item : (List<?>) sourceValue) {
                Media media = convertToMedia(item, config);
                if (media != null) {
                    mediaList.add(media);
                }
            }
        }
        // 支持单个 MediaConvertible 对象
        else {
            Media media = convertToMedia(sourceValue, config);
            if (media != null) {
                mediaList.add(media);
            }
        }

        logger.debug("Extracted {} media items for criterion '{}'",
                mediaList.size(), context.getCriterion().getName());

        return mediaList;
    }

    /**
     * 将对象转换为Media
     * 仅支持 MediaConvertible 接口的实现类和 Media 类型
     */
    protected Media convertToMedia(Object obj, MultimodalConfig config) {
        // 如果已经是Media类型，直接返回
        if (obj instanceof Media) {
            return filterByMimeType((Media) obj, config);
        }

        // 如果实现了 MediaConvertible 接口，调用 toMedia 方法
        if (obj instanceof MediaConvertible) {
            MediaConvertible convertible = (MediaConvertible) obj;
            Media media = convertible.toMedia();
            if (media != null) {
                return filterByMimeType(media, config);
            }
        }

        // 不支持的类型，记录日志并返回null
        logger.debug("Object of type {} does not implement MediaConvertible interface, skipping",
                obj != null ? obj.getClass().getName() : "null");
        return null;
    }

    /**
     * 根据MIME类型过滤
     */
    private Media filterByMimeType(Media media, MultimodalConfig config) {
        if (config.getSupportedMimeTypes() == null || config.getSupportedMimeTypes().isEmpty()) {
            return media;
        }

        String mimeType = media.getMimeType().toString();
        if (config.getSupportedMimeTypes().contains(mimeType)) {
            return media;
        }

        logger.debug("Media with MIME type '{}' not in supported types: {}",
                mimeType, config.getSupportedMimeTypes());
        return null;
    }

    /**
     * 执行多模态评估
     */
    protected CriterionResult evaluateWithMultimodal(CriterionExecutionContext context,
                                                      List<Media> mediaList) {
        CriterionResult result = new CriterionResult();
        result.setCriterionName(context.getCriterion().getName());
        result.setStartTimeMillis(System.currentTimeMillis());

        try {
            // 构建文本prompt
            String textPrompt = buildPrompt(context);
            result.setRawPrompt(textPrompt);

            logger.debug("Evaluating multimodal criterion {} with {} media items, prompt: {}",
                    context.getCriterion().getName(), mediaList.size(), textPrompt);

            // 构建带媒体的消息
            UserMessage userMessage = UserMessage.builder()
                    .text(textPrompt)
                    .media(mediaList)
                    .build();

            // 调用多模态模型
            Prompt prompt = new Prompt(List.of(userMessage));
            ChatResponse chatResponse = multimodalChatModel.call(prompt);
            String responseText = chatResponse.getResult().getOutput().getText();

            if (responseText == null || responseText.trim().isEmpty()) {
                logger.warn("Multimodal ChatModel returned empty response for criterion {}",
                        context.getCriterion().getName());
                result.setStatus(CriterionStatus.ERROR);
                result.setErrorMessage("Multimodal LLM response was empty");
                return result;
            }

            result.setRawResponse(responseText);

            // 解析响应
            ParsedResponse parsedResponse = parseStructuredResponse(responseText, context.getCriterion());
            result.setValue(parsedResponse.getValue());
            if (parsedResponse.getReasoning() != null) {
                result.setReason(parsedResponse.getReasoning());
            }

            result.setStatus(CriterionStatus.SUCCESS);

        } catch (Exception e) {
            logger.error("Error in multimodal evaluation for criterion {}: {}",
                    context.getCriterion().getName(), e.getMessage(), e);
            result.setStatus(CriterionStatus.ERROR);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTimeMillis(System.currentTimeMillis());
        }

        return result;
    }
}

