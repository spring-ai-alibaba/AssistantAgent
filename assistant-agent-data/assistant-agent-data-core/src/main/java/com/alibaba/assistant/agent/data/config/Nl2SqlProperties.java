package com.alibaba.assistant.agent.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for NL2SQL.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "spring.assistant-agent.data.nl2sql")
public class Nl2SqlProperties {

    /** Enable NL2SQL feature */
    private boolean enabled = true;

    /** Schema filter threshold (number of tables) */
    private int schemaFilterThreshold = 10;

    /** LLM configuration */
    private LlmProperties llm = new LlmProperties();

    /** Cache configuration */
    private CacheProperties cache = new CacheProperties();

    @Data
    public static class LlmProperties {
        /** LLM model name */
        private String model = "qwen-max";

        /** Temperature (0.0-1.0, lower = more deterministic) */
        private double temperature = 0.1;

        /** Max tokens for response */
        private int maxTokens = 2000;
    }

    @Data
    public static class CacheProperties {
        /** Enable result caching */
        private boolean enabled = true;

        /** Cache TTL in minutes */
        private int ttlMinutes = 30;
    }
}
