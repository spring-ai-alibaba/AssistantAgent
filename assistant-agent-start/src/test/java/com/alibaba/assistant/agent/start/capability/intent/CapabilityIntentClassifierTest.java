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
package com.alibaba.assistant.agent.start.capability.intent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityIntentClassifierTest {

    private final CapabilityIntentClassifier classifier = new CapabilityIntentClassifier();

    @Test
    void shouldClassifyOperationWhenInputContainsSubmitAction() {
        CapabilityIntentType type = classifier.classify("发起一份OA系统工作汇报");

        assertThat(type).isEqualTo(CapabilityIntentType.OPERATION);
    }

    @Test
    void shouldClassifyQueryWhenInputContainsQueryWords() {
        CapabilityIntentType type = classifier.classify("查询今天的请假记录");

        assertThat(type).isEqualTo(CapabilityIntentType.QUERY);
    }

    @Test
    void shouldClassifyAnalysisWhenInputContainsAnalysisWords() {
        CapabilityIntentType type = classifier.classify("分析本周工作效率趋势");

        assertThat(type).isEqualTo(CapabilityIntentType.ANALYSIS);
    }

}
