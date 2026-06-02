package com.alibaba.assistant.agent.extension.experience.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ExperienceArtifact - 经验可执行产物（FastPath Intent 使用）
 *
 * <p>设计目标：
 * <ul>
 *     <li>REACT：可直接构造 AssistantMessage(toolCalls + 可选文本) 进入 tool 执行</li>
 *     <li>TOOL：提供工具连接信息和Schema，用于创建 CodeactTool</li>
 * </ul>
 *
 * <p>注意：skill package 的文件资源由顶层 {@link Experience#getReferences()} 与
 * {@link Experience#getAssets()} 承载，artifact 不再保有 packageBundle 字段。
 */
public class ExperienceArtifact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ReactArtifact react;

    private ToolArtifact tool;

    public ReactArtifact getReact() {
        return react;
    }

    public void setReact(ReactArtifact react) {
        this.react = react;
    }

    public ToolArtifact getTool() {
        return tool;
    }

    public void setTool(ToolArtifact tool) {
        this.tool = tool;
    }

    public static class ReactArtifact implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Optional assistant text shown to the model/user (align with AssistantMessage text + toolCalls)
         */
        private String assistantText;

        private ToolPlan plan;

        public String getAssistantText() {
            return assistantText;
        }

        public void setAssistantText(String assistantText) {
            this.assistantText = assistantText;
        }

        public ToolPlan getPlan() {
            return plan;
        }

        public void setPlan(ToolPlan plan) {
            this.plan = plan;
        }
    }

    public static class ToolPlan implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private List<ToolCallSpec> toolCalls = new ArrayList<>();

        public List<ToolCallSpec> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCallSpec> toolCalls) {
            this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
        }
    }

    public static class ToolCallSpec implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String toolName;

        /**
         * Arguments map, will be serialized to JSON string for AssistantMessage.ToolCall
         */
        private Map<String, Object> arguments;

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public void setArguments(Map<String, Object> arguments) {
            this.arguments = arguments;
        }
    }

    /**
     * ToolArtifact - TOOL 类型经验的技术产物
     * 包含工具来源、连接信息、Schema 等运行时需要的技术信息
     */
    public static class ToolArtifact implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 工具来源类型：mcp / a2a / http / cli
         */
        private String source;

        // --- MCP 连接信息 ---
        private String mcpServerCode;
        private String mcpServerName;
        private String mcpToolName;

        // --- A2A 连接信息 ---
        private String a2aAgentCardUrl;
        private String a2aAgentName;
        private String a2aSkillName;

        // --- HTTP 连接信息 ---
        private String httpMethod;
        private String httpUrl;
        private String httpBodyTemplate;

        // --- CLI 运行时信息 ---
        /**
         * 该 TOOL 绑定的 CLI provider id
         * 一个 provider 对应唯一的 TOOL experience（{@code cli_<providerId>_tool}）。
         */
        private String providerId;

        /**
         * 允许执行的命令前缀正则（必须以 ^ 锚定，例如 "^dbs($|\\s)"）。
         * 模型提交的 {@code command} 参数在分段后，首个 token 必须匹配该正则才会被执行。
         */
        private String commandAllowPattern;

        /**
         * 允许作为管道下游的外部可执行文件白名单（如 jq/grep 等）。
         * 若为 {@code null} 或空，则仅允许没有管道的命令。
         */
        private java.util.List<String> pipeAllowlist;

        private String runnerImage;
        private String sandboxTemplate;
        private String executionMode;
        private String authProfile;
        private String authProvider;
        private String loginCommandTemplate;
        private String outputFormat;

        /**
         * 工具输入 Schema（JSON Schema 格式字符串）
         */
        private String inputSchema;

        /**
         * 返回值描述
         */
        private String returnDescription;

        /**
         * 是否直接返回结果给用户（不经过 LLM 总结）
         */
        private boolean returnDirect;

        /**
         * 对应的 CodeactTool 名称（注册到 ToolRegistry 的名称）
         */
        private String codeactToolName;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getMcpServerCode() {
            return mcpServerCode;
        }

        public void setMcpServerCode(String mcpServerCode) {
            this.mcpServerCode = mcpServerCode;
        }

        public String getMcpServerName() {
            return mcpServerName;
        }

        public void setMcpServerName(String mcpServerName) {
            this.mcpServerName = mcpServerName;
        }

        public String getMcpToolName() {
            return mcpToolName;
        }

        public void setMcpToolName(String mcpToolName) {
            this.mcpToolName = mcpToolName;
        }

        public String getA2aAgentCardUrl() {
            return a2aAgentCardUrl;
        }

        public void setA2aAgentCardUrl(String a2aAgentCardUrl) {
            this.a2aAgentCardUrl = a2aAgentCardUrl;
        }

        public String getA2aAgentName() {
            return a2aAgentName;
        }

        public void setA2aAgentName(String a2aAgentName) {
            this.a2aAgentName = a2aAgentName;
        }

        public String getA2aSkillName() {
            return a2aSkillName;
        }

        public void setA2aSkillName(String a2aSkillName) {
            this.a2aSkillName = a2aSkillName;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public void setHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
        }

        public String getHttpUrl() {
            return httpUrl;
        }

        public void setHttpUrl(String httpUrl) {
            this.httpUrl = httpUrl;
        }

        public String getHttpBodyTemplate() {
            return httpBodyTemplate;
        }

        public void setHttpBodyTemplate(String httpBodyTemplate) {
            this.httpBodyTemplate = httpBodyTemplate;
        }

        public String getRunnerImage() {
            return runnerImage;
        }

        public void setRunnerImage(String runnerImage) {
            this.runnerImage = runnerImage;
        }

        public String getSandboxTemplate() {
            return sandboxTemplate;
        }

        public void setSandboxTemplate(String sandboxTemplate) {
            this.sandboxTemplate = sandboxTemplate;
        }

        public String getExecutionMode() {
            return executionMode;
        }

        public void setExecutionMode(String executionMode) {
            this.executionMode = executionMode;
        }

        public String getAuthProfile() {
            return authProfile;
        }

        public void setAuthProfile(String authProfile) {
            this.authProfile = authProfile;
        }

        public String getAuthProvider() {
            return authProvider;
        }

        public void setAuthProvider(String authProvider) {
            this.authProvider = authProvider;
        }

        public String getLoginCommandTemplate() {
            return loginCommandTemplate;
        }

        public void setLoginCommandTemplate(String loginCommandTemplate) {
            this.loginCommandTemplate = loginCommandTemplate;
        }

        public String getProviderId() {
            return providerId;
        }

        public void setProviderId(String providerId) {
            this.providerId = providerId;
        }

        public String getCommandAllowPattern() {
            return commandAllowPattern;
        }

        public void setCommandAllowPattern(String commandAllowPattern) {
            this.commandAllowPattern = commandAllowPattern;
        }

        public java.util.List<String> getPipeAllowlist() {
            return pipeAllowlist;
        }

        public void setPipeAllowlist(java.util.List<String> pipeAllowlist) {
            this.pipeAllowlist = pipeAllowlist;
        }

        public String getOutputFormat() {
            return outputFormat;
        }

        public void setOutputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
        }

        public String getInputSchema() {
            return inputSchema;
        }

        public void setInputSchema(String inputSchema) {
            this.inputSchema = inputSchema;
        }

        public String getReturnDescription() {
            return returnDescription;
        }

        public void setReturnDescription(String returnDescription) {
            this.returnDescription = returnDescription;
        }

        public boolean isReturnDirect() {
            return returnDirect;
        }

        public void setReturnDirect(boolean returnDirect) {
            this.returnDirect = returnDirect;
        }

        public String getCodeactToolName() {
            return codeactToolName;
        }

        public void setCodeactToolName(String codeactToolName) {
            this.codeactToolName = codeactToolName;
        }
    }

}
