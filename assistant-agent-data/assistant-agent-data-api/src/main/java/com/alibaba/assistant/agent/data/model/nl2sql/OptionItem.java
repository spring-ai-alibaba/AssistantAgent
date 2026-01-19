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
package com.alibaba.assistant.agent.data.model.nl2sql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple option item for parameter selection in NL2SQL context.
 * <p>
 * Represents a selectable option with a display label and corresponding value.
 * Used in parameter collection for NL2SQL query generation, such as selecting
 * database names, table names, or enumerated column values.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionItem {

    /**
     * The display label shown to the user.
     */
    private String label;

    /**
     * The actual value to be used when this option is selected.
     */
    private String value;
}
