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
package com.alibaba.assistant.agent.core.executor;

import java.util.Objects;

/**
 * Codeact 变量元数据
 * 
 * <p>用于描述一个可用变量的信息，主要用于 Prompt 构建，
 * 让 LLM 知道有哪些变量可以在生成的代码中使用。
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建变量元数据
 * VariableMetadata userIdMeta = VariableMetadata.of("user_id", "str", "用户工号");
 * VariableMetadata inputTextMeta = VariableMetadata.of("input_text", "str", "用户输入文本");
 * 
 * // 格式化为 Prompt 字符串
 * String prompt = userIdMeta.formatForPrompt();
 * // 结果: "user_id(str): 用户工号"
 * }</pre>
 * 
 * @author Assistant Agent Team
 * @since 1.0.0
 * @see CodeactVariableProvider
 */
public class VariableMetadata {

    /**
     * 变量名（Python 变量名）
     */
    private final String name;

    /**
     * 变量描述（用于 Prompt 展示，告诉 LLM 这个变量是什么）
     */
    private final String description;

    /**
     * Python 类型（str, int, dict, list 等）
     */
    private final String pythonType;

    private VariableMetadata(String name, String description, String pythonType) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.pythonType = pythonType != null ? pythonType : "Any";
    }

    /**
     * 获取变量名
     * 
     * @return 变量名
     */
    public String getName() {
        return name;
    }

    /**
     * 获取变量描述
     * 
     * @return 变量描述，可能为 null
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取 Python 类型
     * 
     * @return Python 类型，默认为 "Any"
     */
    public String getPythonType() {
        return pythonType;
    }

    /**
     * 创建变量元数据
     * 
     * @param name 变量名（必须是有效的 Python 标识符）
     * @param pythonType Python 类型（str, int, dict, list 等）
     * @param description 描述（用于 Prompt 展示）
     * @return VariableMetadata 实例
     */
    public static VariableMetadata of(String name, String pythonType, String description) {
        return new VariableMetadata(name, description, pythonType);
    }

    /**
     * 创建变量元数据（无描述）
     * 
     * @param name 变量名
     * @param pythonType Python 类型
     * @return VariableMetadata 实例
     */
    public static VariableMetadata of(String name, String pythonType) {
        return new VariableMetadata(name, null, pythonType);
    }

    /**
     * 格式化为 Prompt 友好的字符串
     * 
     * <p>格式: {@code name(type): description} 或 {@code name(type)}（无描述时）
     * 
     * <p>示例: {@code input_text(str): 本轮的文本全部输入内容的组合文本}
     * 
     * @return 格式化后的字符串
     */
    public String formatForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("(").append(pythonType).append(")");
        if (description != null && !description.isEmpty()) {
            sb.append(": ").append(description);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VariableMetadata that = (VariableMetadata) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(pythonType, that.pythonType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, pythonType);
    }

    @Override
    public String toString() {
        return "VariableMetadata{" +
                "name='" + name + '\'' +
                ", pythonType='" + pythonType + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
