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
package com.alibaba.assistant.agent.planning.spi;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParameterOptionsServiceTest {

    @Test
    void shouldHaveRequiredMethods() throws NoSuchMethodException {
        // Verify interface has required methods
        ParameterOptionsService.class.getMethod("fetchOptions", OptionsSourceConfig.class);
        ParameterOptionsService.class.getMethod("supports", OptionsSourceConfig.SourceType.class);
        ParameterOptionsService.class.getMethod("getName");
    }

    @Test
    void shouldBeImplementable() {
        // Simple implementation test
        ParameterOptionsService service = new ParameterOptionsService() {
            @Override
            public List<OptionItem> fetchOptions(OptionsSourceConfig config) {
                return List.of();
            }

            @Override
            public boolean supports(OptionsSourceConfig.SourceType sourceType) {
                return true;
            }

            @Override
            public String getName() {
                return "TestService";
            }
        };

        assertNotNull(service);
        assertEquals("TestService", service.getName());
        assertTrue(service.supports(OptionsSourceConfig.SourceType.NL2SQL));
        assertEquals(0, service.fetchOptions(null).size());
    }
}
