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
package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.extension.experience.internal.InMemoryExperienceRepository;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceScope;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoExperienceConfigTest {

	@Test
	void shouldNotCreateWorkReportFastIntentExperienceAndKeepXiaomingExperience() throws Exception {
		InMemoryExperienceRepository repository = new InMemoryExperienceRepository();
		DemoExperienceConfig config = new DemoExperienceConfig(repository);

		config.run();

		List<Experience> reactExperiences = repository.findByTypeAndScope(ExperienceType.REACT, ExperienceScope.GLOBAL,
				null, null);
		boolean hasWorkReportReactExp = reactExperiences.stream()
				.anyMatch(exp -> "工作汇报快速发起".equals(exp.getTitle()));
		assertFalse(hasWorkReportReactExp);

		Experience xiaomingExp = reactExperiences.stream()
				.filter(exp -> "小明系数计算策略".equals(exp.getTitle()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("xiaoming react experience not found"));

		assertNotNull(xiaomingExp.getFastIntentConfig());
		assertTrue(xiaomingExp.getFastIntentConfig().isEnabled());
		assertNotNull(xiaomingExp.getFastIntentConfig().getMatch());
		assertNotNull(xiaomingExp.getFastIntentConfig().getMatch().getCondition());
		assertEquals("message_regex", xiaomingExp.getFastIntentConfig().getMatch().getCondition().getType());
		assertTrue(xiaomingExp.getFastIntentConfig().getMatch().getCondition().getPattern().contains("小明系数"));

		assertNotNull(xiaomingExp.getArtifact());
		assertNotNull(xiaomingExp.getArtifact().getReact());
		assertNotNull(xiaomingExp.getArtifact().getReact().getPlan());
		List<ExperienceArtifact.ToolCallSpec> toolCalls = xiaomingExp.getArtifact().getReact().getPlan().getToolCalls();
		assertNotNull(toolCalls);
		assertFalse(toolCalls.isEmpty());
		assertEquals("write_code", toolCalls.get(0).getToolName());
		assertNotNull(toolCalls.get(0).getArguments());
		assertFalse(toolCalls.get(0).getArguments().isEmpty());
	}

}
