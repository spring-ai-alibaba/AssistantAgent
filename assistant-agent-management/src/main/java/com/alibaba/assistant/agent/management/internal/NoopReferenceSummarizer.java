package com.alibaba.assistant.agent.management.internal;

import com.alibaba.assistant.agent.management.spi.ReferenceSummarizer;

/**
 * 默认实现：不调用 LLM，始终返回 null，让调用方走回退文案。
 */
public class NoopReferenceSummarizer implements ReferenceSummarizer {

    @Override
    public String summarize(String path, String content) {
        return null;
    }
}
