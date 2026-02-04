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
package com.alibaba.assistant.agent.autoconfigure.subagent.filter;

/**
 * 工具白名单模式枚举
 * 
 * <p>定义工具白名单的筛选模式，用于控制名称白名单和组白名单的组合逻辑。
 * 
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public enum WhitelistMode {
    
    /**
     * 交集模式（默认）
     * 
     * <p>当名称白名单和组白名单同时存在时，工具必须同时满足两者。
     * 当只有其中一个存在时，只检查存在的那个。
     */
    INTERSECTION,
    
    /**
     * 并集模式
     * 
     * <p>当名称白名单和组白名单同时存在时，工具满足任一即可。
     * 当只有其中一个存在时，只检查存在的那个。
     */
    UNION,
    
    /**
     * 仅名称模式
     * 
     * <p>仅使用名称白名单进行筛选，忽略组白名单。
     */
    NAME_ONLY,
    
    /**
     * 仅组模式
     * 
     * <p>仅使用组白名单进行筛选，忽略名称白名单。
     */
    GROUP_ONLY
}
