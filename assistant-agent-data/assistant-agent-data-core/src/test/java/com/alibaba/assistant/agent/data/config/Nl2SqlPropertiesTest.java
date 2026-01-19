package com.alibaba.assistant.agent.data.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Nl2SqlPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        Nl2SqlProperties properties = new Nl2SqlProperties();

        assertTrue(properties.isEnabled());
        assertEquals(10, properties.getSchemaFilterThreshold());
        assertNotNull(properties.getLlm());
        assertEquals("qwen-max", properties.getLlm().getModel());
        assertEquals(0.1, properties.getLlm().getTemperature());
        assertEquals(2000, properties.getLlm().getMaxTokens());
        assertNotNull(properties.getCache());
        assertTrue(properties.getCache().isEnabled());
        assertEquals(30, properties.getCache().getTtlMinutes());
    }

    @Test
    void shouldSetProperties() {
        Nl2SqlProperties properties = new Nl2SqlProperties();

        properties.setEnabled(false);
        properties.setSchemaFilterThreshold(20);
        properties.getLlm().setModel("qwen-plus");
        properties.getLlm().setTemperature(0.5);
        properties.getCache().setEnabled(false);

        assertFalse(properties.isEnabled());
        assertEquals(20, properties.getSchemaFilterThreshold());
        assertEquals("qwen-plus", properties.getLlm().getModel());
        assertEquals(0.5, properties.getLlm().getTemperature());
        assertFalse(properties.getCache().isEnabled());
    }
}
