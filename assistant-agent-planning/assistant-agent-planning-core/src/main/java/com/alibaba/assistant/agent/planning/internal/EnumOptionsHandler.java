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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler for Java enum-based option sources.
 * <p>
 * Uses reflection to get enum constants and convert them to OptionItem list.
 * Both label and value are set to the enum constant name.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
class EnumOptionsHandler implements OptionsSourceHandler {

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        String enumClassName = (String) specificConfig;

        try {
            Class<?> enumClass = Class.forName(enumClassName);

            if (!enumClass.isEnum()) {
                throw new OptionsSourceException("Class is not an enum: " + enumClassName);
            }

            Object[] constants = enumClass.getEnumConstants();
            return Arrays.stream(constants)
                    .map(c -> {
                        String name = c.toString();
                        return new OptionItem(name, name);
                    })
                    .collect(Collectors.toList());
        } catch (ClassNotFoundException e) {
            throw new OptionsSourceException("Enum class not found: " + enumClassName, e);
        }
    }

    @Override
    public OptionsSourceConfig.SourceType supportedType() {
        return OptionsSourceConfig.SourceType.ENUM;
    }
}
