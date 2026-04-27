package com.alibaba.assistant.agent.core.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraalCodeExecutorStringLiteralTest {

    @Test
    void shouldEscapeTrailingDoubleQuoteSafely() {
        String literal = GraalCodeExecutor.toPythonStringLiteral(
                "帮我查询我参与的2026-03-06至2026-03-13期间进行中的发布计划\"");

        assertEquals("\"帮我查询我参与的2026-03-06至2026-03-13期间进行中的发布计划\\\"\"", literal);
    }

    @Test
    void shouldEscapeMultilineStringsSafely() {
        String literal = GraalCodeExecutor.toPythonStringLiteral("用户原始需求: 第一行\n第二行\"结尾");

        assertEquals("\"用户原始需求: 第一行\\n第二行\\\"结尾\"", literal);
    }
}
