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
package com.alibaba.assistant.agent.evaluation.model;

import java.util.Arrays;
import java.util.List;

/**
 * 多模态配置
 * 用于配置评估项的多模态处理行为
 *
 * @author Assistant Agent Team
 */
public class MultimodalConfig {

    /**
     * 是否启用多模态处理
     */
    private boolean enabled = false;

    /**
     * 多模态输入的来源路径
     * 例如："context.input.attachments"
     *
     * 注意：sourcePath指向的对象必须是 MediaConvertible 接口的实现类，
     * 或者是 List&lt;MediaConvertible&gt; 类型的集合。
     * 如果对象未实现 MediaConvertible 接口，将被忽略。
     */
    private String sourcePath;

    /**
     * 多模态模型的evaluator引用
     * 如果为空，则使用默认的多模态evaluator
     */
    private String evaluatorRef;

    /**
     * 支持的媒体类型列表
     * 例如：["image/jpeg", "image/png"]
     * 如果为空，默认支持所有图片类型
     */
    private List<String> supportedMimeTypes;

    /**
     * 默认构造函数
     */
    public MultimodalConfig() {
    }

    /**
     * 创建图片类多模态配置
     *
     * @param sourcePath 附件来源路径，如 "context.input.attachments"
     * @return MultimodalConfig实例
     */
    public static MultimodalConfig forImages(String sourcePath) {
        MultimodalConfig config = new MultimodalConfig();
        config.enabled = true;
        config.sourcePath = sourcePath;
        config.supportedMimeTypes = Arrays.asList("image/jpeg", "image/png", "image/jpg");
        return config;
    }

    /**
     * 创建自定义的多模态配置
     *
     * @param sourcePath 附件来源路径
     * @param mimeTypes 支持的MIME类型列表
     * @return MultimodalConfig实例
     */
    public static MultimodalConfig of(String sourcePath, List<String> mimeTypes) {
        MultimodalConfig config = new MultimodalConfig();
        config.enabled = true;
        config.sourcePath = sourcePath;
        config.supportedMimeTypes = mimeTypes;
        return config;
    }

    /**
     * 创建图片类多模态配置并指定evaluator
     *
     * @param sourcePath 附件来源路径
     * @param evaluatorRef evaluator引用
     * @return MultimodalConfig实例
     */
    public static MultimodalConfig forImages(String sourcePath, String evaluatorRef) {
        MultimodalConfig config = forImages(sourcePath);
        config.evaluatorRef = evaluatorRef;
        return config;
    }

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getEvaluatorRef() {
        return evaluatorRef;
    }

    public void setEvaluatorRef(String evaluatorRef) {
        this.evaluatorRef = evaluatorRef;
    }

    public List<String> getSupportedMimeTypes() {
        return supportedMimeTypes;
    }

    public void setSupportedMimeTypes(List<String> supportedMimeTypes) {
        this.supportedMimeTypes = supportedMimeTypes;
    }
}

