package com.alibaba.assistant.agent.extension.learning.extractor;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ExperienceLearningExtractorTest {

    @Test
    void shouldParseToolExperienceType() throws Exception {
        Experience experience = buildExperience(Map.of(
                "type", "TOOL",
                "title", "tool usage",
                "summary", "summary",
                "content", "content",
                "tags", new ArrayList<>(List.of("tool"))
        ));

        assertEquals(ExperienceType.TOOL, experience.getType());
        assertEquals("tool usage", experience.getTitle());
    }

    @Test
    void shouldRejectLegacyCodeExperienceType() {
        assertThrows(Exception.class, () -> buildExperience(Map.of(
                "type", "CODE",
                "title", "legacy code",
                "summary", "summary",
                "content", "content"
        )));
    }

    private static Experience buildExperience(Map<String, Object> item) throws Exception {
        ExperienceLearningExtractor extractor = new ExperienceLearningExtractor(mock(ChatModel.class));
        Method method = ExperienceLearningExtractor.class.getDeclaredMethod("buildExperienceFromMap", Map.class);
        method.setAccessible(true);
        return (Experience) method.invoke(extractor, item);
    }
}
