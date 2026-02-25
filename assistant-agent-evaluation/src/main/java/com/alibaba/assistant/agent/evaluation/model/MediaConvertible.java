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

import org.springframework.ai.content.Media;

/**
 * 可转换为Media的对象接口
 *
 * 用于多模态评估场景，任何需要作为多模态输入的附件类型都应实现此接口。
 * MultimodalLLMBasedEvaluator 在处理多模态输入时，会检查对象是否实现了此接口，
 * 只有实现了此接口的对象才会被转换为Media并传递给多模态模型。
 *
 * @author Assistant Agent Team
 */
public interface MediaConvertible {

    /**
     * 将对象转换为Spring AI的Media对象
     *
     * @return Media对象，如果无法转换（如非图片类型）则返回null
     */
    Media toMedia();

    /**
     * 判断是否为支持的多模态类型（如图片）
     *
     * @return 如果支持多模态处理返回true，否则返回false
     */
    default boolean isMultimodalSupported() {
        return toMedia() != null;
    }
}

