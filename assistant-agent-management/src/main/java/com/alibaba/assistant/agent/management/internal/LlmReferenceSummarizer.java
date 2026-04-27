package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.management.spi.ReferenceSummarizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * 基于 Spring AI {@link ChatModel} 的参考文档摘要生成器。
 *
 * <p>当 {@link DescriptionResolver} 无法从 .md H1 / frontmatter 提取标题时调用；
 * 仅处理纯文本/代码/标记类文件（其它二进制资源不会传入，非 UTF-8 文本由调用方过滤）。
 * 任何异常都会被调用方捕获并回退到 {@code "(no description) path"} 文案。
 */
public class LlmReferenceSummarizer implements ReferenceSummarizer {

    private static final Logger log = LoggerFactory.getLogger(LlmReferenceSummarizer.class);

    /**
     * 只向 LLM 传前 N 个字符，避免超长文件导致的上下文浪费。
     */
    private static final int MAX_CONTENT_CHARS = 4000;

    private static final int MAX_SUMMARY_CHARS = 200;

    private static final String SYSTEM_PROMPT = ""
            + "You summarize skill reference documents (markdown/yaml/text/code).\n"
            + "Return a single-line Chinese description, <= 80 characters,\n"
            + "that tells an AI agent what this file explains or provides so it can decide whether to read it.\n"
            + "Do NOT start with quotes, labels, or boilerplate like 'This file'.\n"
            + "Do NOT invent information absent from the content. No trailing punctuation.";

    private final ChatModel chatModel;

    public LlmReferenceSummarizer(ChatModel chatModel) {
        if (chatModel == null) {
            throw new IllegalArgumentException("ChatModel is required for LlmReferenceSummarizer");
        }
        this.chatModel = chatModel;
    }

    @Override
    public String summarize(String path, String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.length() > MAX_CONTENT_CHARS
                ? content.substring(0, MAX_CONTENT_CHARS) + "\n...[truncated]"
                : content;
        String userPrompt = "path: " + (path != null ? path : "(unknown)") + "\n\n---\n" + trimmed;
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(userPrompt)));
            ChatResponse response = chatModel.call(prompt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return null;
            }
            String text = response.getResult().getOutput().getText();
            return clean(text);
        } catch (Exception e) {
            log.warn("LlmReferenceSummarizer#summarize - path={}, error={}", path, e.toString());
            return null;
        }
    }

    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        // Collapse to first non-empty line
        int nl = s.indexOf('\n');
        if (nl >= 0) {
            s = s.substring(0, nl).trim();
        }
        // Strip wrapping quotes
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        if (s.length() > MAX_SUMMARY_CHARS) {
            s = s.substring(0, MAX_SUMMARY_CHARS).trim();
        }
        return s.isEmpty() ? null : s;
    }
}
