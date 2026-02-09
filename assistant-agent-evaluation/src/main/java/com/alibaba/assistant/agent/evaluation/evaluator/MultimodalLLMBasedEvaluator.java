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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * 用于将 Map 转换为 MediaConvertible 实现类的 ObjectMapper
     */
    private final ObjectMapper objectMapper;

    /**
     * 已注册的 MediaConvertible 实现类类型
     * 用于将反序列化后的 Map 转换回正确的类型
     */
    private static final Map<String, Class<? extends MediaConvertible>> registeredMediaTypes = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param textModel 纯文本模型，用于普通评估
     * @param multimodalModel 多模态模型，用于处理图片等多模态输入
     * @param evaluatorId 评估器ID
     */
    public MultimodalLLMBasedEvaluator(ChatModel textModel, ChatModel multimodalModel, String evaluatorId) {
        this(textModel, multimodalModel, evaluatorId, createDefaultObjectMapper());
    }

    /**
     * 创建默认的 ObjectMapper，配置为忽略未知属性
     * 这是必要的，因为序列化时可能会包含额外的属性（如来自接口默认方法的 getter）
     */
    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 忽略未知属性，避免反序列化时因为额外的属性而失败
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * 构造函数（带自定义 ObjectMapper）
     *
     * @param textModel 纯文本模型，用于普通评估
     * @param multimodalModel 多模态模型，用于处理图片等多模态输入
     * @param evaluatorId 评估器ID
     * @param objectMapper 用于类型转换的 ObjectMapper
     */
    public MultimodalLLMBasedEvaluator(ChatModel textModel, ChatModel multimodalModel, 
                                        String evaluatorId, ObjectMapper objectMapper) {
        super(textModel, evaluatorId);
        this.multimodalChatModel = multimodalModel;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * 注册 MediaConvertible 实现类
     * 用于支持将序列化后的 Map 转换回正确的类型
     *
     * @param typeIdentifier 类型标识符（通常是完整类名或简短别名）
     * @param clazz MediaConvertible 实现类
     */
    public static void registerMediaType(String typeIdentifier, Class<? extends MediaConvertible> clazz) {
        registeredMediaTypes.put(typeIdentifier, clazz);
        logger.info("Registered MediaConvertible type: {} -> {}", typeIdentifier, clazz.getName());
    }

    /**
     * 注册 MediaConvertible 实现类（使用类的简单名称作为标识符）
     *
     * @param clazz MediaConvertible 实现类
     */
    public static void registerMediaType(Class<? extends MediaConvertible> clazz) {
        registerMediaType(clazz.getSimpleName(), clazz);
        // 同时注册完整类名
        registerMediaType(clazz.getName(), clazz);
    }

    /**
     * 获取已注册的类型数量（用于测试）
     */
    public static int getRegisteredTypeCount() {
        return registeredMediaTypes.size();
    }

    /**
     * 清除所有已注册的类型（用于测试）
     */
    public static void clearRegisteredTypes() {
        registeredMediaTypes.clear();
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
     * 支持 MediaConvertible 接口的实现类、Media 类型、以及可以转换为 MediaConvertible 的 Map 类型
     *
     * 当对象是 Map 类型时（通常是因为序列化/反序列化导致类型信息丢失），
     * 会尝试使用已注册的 MediaConvertible 实现类进行转换。
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
            return null;
        }

        // 如果是 Map 类型（通常是因为序列化/反序列化导致类型信息丢失）
        // 尝试使用已注册的 MediaConvertible 实现类进行转换
        if (obj instanceof Map) {
            Media media = tryConvertMapToMedia((Map<?, ?>) obj, config);
            if (media != null) {
                return media;
            }
            // 如果转换失败，记录更详细的日志
            logger.debug("Object of type {} could not be converted to MediaConvertible. " +
                    "Make sure to register the target type using MultimodalLLMBasedEvaluator.registerMediaType()",
                    obj.getClass().getName());
            return null;
        }

        // 不支持的类型，记录日志并返回null
        logger.debug("Object of type {} does not implement MediaConvertible interface, skipping",
                obj != null ? obj.getClass().getName() : "null");
        return null;
    }

    /**
     * 尝试将 Map 转换为 MediaConvertible 并获取 Media
     *
     * @param map 要转换的 Map
     * @param config 多模态配置
     * @return Media 对象，如果转换失败则返回 null
     */
    private Media tryConvertMapToMedia(Map<?, ?> map, MultimodalConfig config) {
        if (registeredMediaTypes.isEmpty()) {
            logger.debug("No MediaConvertible types registered for Map conversion");
            return null;
        }

        // 尝试使用所有已注册的类型进行转换
        for (Map.Entry<String, Class<? extends MediaConvertible>> entry : registeredMediaTypes.entrySet()) {
            try {
                MediaConvertible convertible = objectMapper.convertValue(map, entry.getValue());
                Media media = convertible.toMedia();
                if (media != null) {
                    logger.debug("Successfully converted Map to {} and obtained Media", entry.getValue().getSimpleName());
                    return filterByMimeType(media, config);
                }
            } catch (Exception e) {
                // 转换失败，尝试下一个类型
                logger.trace("Failed to convert Map to {}: {}", entry.getValue().getSimpleName(), e.getMessage());
            }
        }

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

            logger.debug("Evaluating multimodal criterion {} with {} media items, prompt: {}, chatModel: {} ({})",
                    context.getCriterion().getName(), mediaList.size(), textPrompt,
                    multimodalChatModel.getClass().getSimpleName(), multimodalChatModel);

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

