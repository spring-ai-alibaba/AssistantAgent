package com.alibaba.assistant.agent.extension.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 经验模块配置属性
 *
 * @author Assistant Agent Team
 */
@ConfigurationProperties(prefix = "spring.ai.alibaba.codeact.extension.experience")
public class ExperienceExtensionProperties {

    /**
     * 经验模块总开关
     */
    private boolean enabled = true;

    /**
     * 代码经验模块开关
     */
    private boolean codeExperienceEnabled = true;

    /**
     * React经验模块开关
     */
    private boolean reactExperienceEnabled = true;

    /**
     * 常识经验模块开关
     */
    private boolean commonExperienceEnabled = true;

    /**
     * FastPath Intent 总开关（默认关闭）
     */
    private boolean fastIntentEnabled = false;

    /**
     * FastPath Intent for REACT（默认跟随 fastIntentEnabled）
     */
    private boolean fastIntentReactEnabled = true;

    /**
     * FastPath Intent for CODE（默认跟随 fastIntentEnabled）
     */
    private boolean fastIntentCodeEnabled = true;

    /**
     * REACT FastPath 允许调用的 tool 白名单；为空表示不限制
     */
    private List<String> fastIntentAllowedTools = new ArrayList<>();

    /**
     * Progressive disclosure 下允许直接以 React function call 形式暴露的工具名白名单。
     *
     * <p>首版采用显式 allowlist：
     * <ul>
     *     <li>未配置的 TOOL 经验仍然只能通过 write_code / execute_code 走 CodeAct 路径</li>
     *     <li>已配置的工具会额外注册到 React 阶段，但运行时仍会根据预取/检索结果做二次裁剪</li>
     * </ul>
     */
    private List<String> reactDirectToolNames = new ArrayList<>();

    /**
     * 单次查询最大返回经验条数
     */
    private int maxItemsPerQuery = 5;

    /**
     * 单条经验最大长度（用于控制Prompt）
     */
    private int maxContentLength = 2000;

    /**
     * {@code read_exp_doc} 单次允许读取的路径数量上限。
     */
    private int readExpDocMaxPaths = 6;

    /**
     * 内存实现相关配置
     */
    private InMemoryConfig inMemory = new InMemoryConfig();

    /**
     * Store实现相关配置
     */
    private StoreConfig store = new StoreConfig();

    /**
     * 日志相关配置
     */
    private LoggingConfig logging = new LoggingConfig();

    /**
     * 内存实现配置
     */
    public static class InMemoryConfig {
        /**
         * 是否启用内存实现
         */
        private boolean enabled = true;

        /**
         * 全局最大经验数
         */
        private int maxTotalExperiences = 1000;

        /**
         * 经验在内存中的存活时间（秒）
         */
        private long ttlSeconds = -1; // -1表示不过期

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxTotalExperiences() {
            return maxTotalExperiences;
        }

        public void setMaxTotalExperiences(int maxTotalExperiences) {
            this.maxTotalExperiences = maxTotalExperiences;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    /**
     * Store实现配置
     */
    public static class StoreConfig {
        /**
         * 是否启用Store实现
         */
        private boolean enabled = false;

        /**
         * 命名空间前缀
         */
        private String namespacePrefix = "experience";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getNamespacePrefix() {
            return namespacePrefix;
        }

        public void setNamespacePrefix(String namespacePrefix) {
            this.namespacePrefix = namespacePrefix;
        }
    }

    /**
     * 日志配置
     */
    public static class LoggingConfig {
        /**
         * 是否输出调试级经验查询日志
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCodeExperienceEnabled() {
        return codeExperienceEnabled;
    }

    public void setCodeExperienceEnabled(boolean codeExperienceEnabled) {
        this.codeExperienceEnabled = codeExperienceEnabled;
    }

    public boolean isReactExperienceEnabled() {
        return reactExperienceEnabled;
    }

    public void setReactExperienceEnabled(boolean reactExperienceEnabled) {
        this.reactExperienceEnabled = reactExperienceEnabled;
    }

    public boolean isCommonExperienceEnabled() {
        return commonExperienceEnabled;
    }

    public void setCommonExperienceEnabled(boolean commonExperienceEnabled) {
        this.commonExperienceEnabled = commonExperienceEnabled;
    }

    public boolean isFastIntentEnabled() {
        return fastIntentEnabled;
    }

    public void setFastIntentEnabled(boolean fastIntentEnabled) {
        this.fastIntentEnabled = fastIntentEnabled;
    }

    public boolean isFastIntentReactEnabled() {
        return fastIntentReactEnabled;
    }

    public void setFastIntentReactEnabled(boolean fastIntentReactEnabled) {
        this.fastIntentReactEnabled = fastIntentReactEnabled;
    }

    public boolean isFastIntentCodeEnabled() {
        return fastIntentCodeEnabled;
    }

    public void setFastIntentCodeEnabled(boolean fastIntentCodeEnabled) {
        this.fastIntentCodeEnabled = fastIntentCodeEnabled;
    }

    public List<String> getFastIntentAllowedTools() {
        return fastIntentAllowedTools;
    }

    public void setFastIntentAllowedTools(List<String> fastIntentAllowedTools) {
        this.fastIntentAllowedTools = fastIntentAllowedTools != null ? fastIntentAllowedTools : new ArrayList<>();
    }

    public List<String> getReactDirectToolNames() {
        return reactDirectToolNames;
    }

    public void setReactDirectToolNames(List<String> reactDirectToolNames) {
        this.reactDirectToolNames = reactDirectToolNames != null ? reactDirectToolNames : new ArrayList<>();
    }

    public int getMaxItemsPerQuery() {
        return maxItemsPerQuery;
    }

    public void setMaxItemsPerQuery(int maxItemsPerQuery) {
        this.maxItemsPerQuery = maxItemsPerQuery;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public int getReadExpDocMaxPaths() {
        return readExpDocMaxPaths;
    }

    public void setReadExpDocMaxPaths(int readExpDocMaxPaths) {
        this.readExpDocMaxPaths = readExpDocMaxPaths;
    }

    public InMemoryConfig getInMemory() {
        return inMemory;
    }

    public void setInMemory(InMemoryConfig inMemory) {
        this.inMemory = inMemory;
    }

    public StoreConfig getStore() {
        return store;
    }

    public void setStore(StoreConfig store) {
        this.store = store;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }
}
