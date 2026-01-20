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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Tests for EnumOptionsHandler.
 *
 * @author Assistant Agent Team
 */
class EnumOptionsHandlerTest {

    private final EnumOptionsHandler handler = new EnumOptionsHandler();

    @Test
    void shouldConvertEnumToOptions() {
        String enumClassName = TestStatus.class.getName();

        List<OptionItem> options = handler.handle("test-system", enumClassName);

        assertThat(options).hasSize(3);
        assertThat(options).extracting(OptionItem::getLabel)
                .containsExactly("ACTIVE", "INACTIVE", "PENDING");
        assertThat(options).extracting(OptionItem::getValue)
                .containsExactly("ACTIVE", "INACTIVE", "PENDING");
    }

    @Test
    void shouldUseSameValueForLabelAndValue() {
        String enumClassName = TestStatus.class.getName();

        List<OptionItem> options = handler.handle("test-system", enumClassName);

        for (OptionItem option : options) {
            assertThat(option.getLabel()).isEqualTo(option.getValue());
        }
    }

    @Test
    void shouldThrowExceptionForInvalidClassName() {
        String invalidClassName = "com.invalid.NonExistentEnum";

        assertThatThrownBy(() -> handler.handle("test-system", invalidClassName))
                .isInstanceOf(OptionsSourceException.class)
                .hasMessageContaining("Enum class not found")
                .hasMessageContaining(invalidClassName)
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void shouldThrowExceptionForNonEnumClass() {
        String nonEnumClassName = String.class.getName();

        assertThatThrownBy(() -> handler.handle("test-system", nonEnumClassName))
                .isInstanceOf(OptionsSourceException.class)
                .hasMessageContaining("Class is not an enum")
                .hasMessageContaining(nonEnumClassName);
    }

    @Test
    void shouldSupportEnumType() {
        assertThat(handler.supportedType()).isEqualTo(OptionsSourceConfig.SourceType.ENUM);
    }

    @Test
    void shouldThrowExceptionForNonStringConfig() {
        Integer invalidConfig = 123; // Not a String

        assertThatThrownBy(() -> handler.handle("test-system", invalidConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected String");
    }

    @Test
    void shouldThrowExceptionForNullClassName() {
        assertThat(catchThrowable(() -> handler.handle("test-system", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected String");
    }

    @Test
    void shouldReturnEmptyListForEmptyEnum() {
        String emptyEnumClassName = EmptyEnum.class.getName();

        List<OptionItem> result = handler.handle("test-system", emptyEnumClassName);

        assertThat(result).isEmpty();
    }

    /**
     * Test enum for verification.
     */
    public enum TestStatus {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    /**
     * Empty enum for edge case testing.
     */
    public enum EmptyEnum {
        // No constants
    }
}
