package com.alibaba.assistant.agent.extension.experience.model;

import java.util.List;

public final class CliRuntimeConstants {

    public static final String SOURCE_CLI = "cli";
    public static final String EXECUTION_MODE_SANDBOX = "SANDBOX";
    public static final String AUTH_PROFILE_USER_TOKEN_BROKER = "USER_TOKEN_BROKER";
    public static final String AUTH_PROFILE_SERVER_STATIC_ENV = "SERVER_STATIC_ENV";
    public static final String SANDBOX_TEMPLATE_CODE_INTERPRETER = "code-interpreter";
    public static final String OUTPUT_FORMAT_TEXT = "text";

    /** 每个 CLI provider 对应的唯一 TOOL 名称前缀/后缀。结果形如 {@code cli_<providerId>_tool}。 */
    public static final String TOOL_NAME_PREFIX = "cli_";
    public static final String TOOL_NAME_SUFFIX = "_tool";

    /** 通用 TOOL experience id 前缀：{@code tool-cli-<providerId>}。 */
    public static final String TOOL_ID_PREFIX = "tool-cli-";

    /** 归类 CLI 工具的 targetClassName（供 CodeactTool 使用）。 */
    public static final String TOOL_GROUP_CLASS_NAME = "cli_tools";

    /** 工具的唯一入参名，承载模型侧提交的 shell 命令。 */
    public static final String COMMAND_PARAM = "command";

    /** 空闲沙箱淘汰时间（秒）的配置键。 */
    public static final String IDLE_TIMEOUT_PROPERTY = "aone.tool.cli.sandbox.idle-timeout-seconds";
    public static final long DEFAULT_IDLE_TIMEOUT_SECONDS = 300L;

    /** 默认允许作为管道下游的可执行文件白名单。 */
    public static final List<String> DEFAULT_PIPE_ALLOWLIST = List.of(
            "jq", "grep", "head", "tail", "sed", "awk", "sort", "uniq", "wc", "tr", "cut"
    );

    public static String toolName(String providerId) {
        return TOOL_NAME_PREFIX + providerId + TOOL_NAME_SUFFIX;
    }

    public static String toolExperienceId(String providerId) {
        return TOOL_ID_PREFIX + providerId;
    }

    private CliRuntimeConstants() {
    }
}
