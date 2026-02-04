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
package com.alibaba.assistant.agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * PromptContributorManager 的默认实现
 * 协调多个 PromptContributor 生成最终的 PromptContribution
 *
 * @author Assistant Agent Team
 */
public class DefaultPromptContributorManager implements PromptContributorManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultPromptContributorManager.class);

    private final List<PromptContributor> contributors;

    public DefaultPromptContributorManager() {
        this.contributors = new CopyOnWriteArrayList<>();
    }

    public DefaultPromptContributorManager(List<PromptContributor> contributors) {
        this.contributors = new CopyOnWriteArrayList<>();
        if (contributors != null) {
            this.contributors.addAll(contributors);
            sortContributors();
        }
    }

    @Override
    public PromptContribution assemble(PromptContributorContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (contributors.isEmpty()) {
            return PromptContribution.empty();
        }

        String systemPrepend = null;
        String systemAppend = null;
        List<Message> prepend = new ArrayList<>();
        List<Message> append = new ArrayList<>();

        for (PromptContributor contributor : contributors) {
            try {
                if (!contributor.shouldContribute(context)) {
                    log.debug("DefaultPromptContributorManager#assemble - reason=contributor 跳过, name={}",
                            contributor.getName());
                    continue;
                }

                PromptContribution c = contributor.contribute(context);
                if (c == null || c.isEmpty()) {
                    log.debug("DefaultPromptContributorManager#assemble - reason=contributor 返回空内容, name={}",
                            contributor.getName());
                    continue;
                }

                log.debug("DefaultPromptContributorManager#assemble - reason=合并 contributor 贡献, name={}",
                        contributor.getName());

                // Merge system text in contributor priority order
                systemPrepend = concatSystemText(systemPrepend, c.systemTextToPrepend());
                systemAppend = concatSystemText(systemAppend, c.systemTextToAppend());

                // Normalize SystemMessage in message lists into systemAppend by default
                normalizeAndAdd(prepend, c.messagesToPrepend());
                normalizeAndAdd(append, c.messagesToAppend());

            } catch (Exception e) {
                log.error("DefaultPromptContributorManager#assemble - reason=contributor 执行失败, name={}",
                        contributor.getName(), e);
            }
        }

        return PromptContribution.builder()
                .systemTextToPrepend(systemPrepend)
                .systemTextToAppend(systemAppend)
                .prependAll(prepend)
                .appendAll(append)
                .build();
    }

    @Override
    public List<PromptContributor> getContributors() {
        return List.copyOf(contributors);
    }

    @Override
    public void register(PromptContributor contributor) {
        Objects.requireNonNull(contributor, "contributor must not be null");
        contributors.add(contributor);
        sortContributors();
        log.info("DefaultPromptContributorManager#register - reason=注册 contributor, name={}",
                contributor.getName());
    }

    @Override
    public void unregister(String contributorName) {
        Objects.requireNonNull(contributorName, "contributorName must not be null");
        boolean removed = contributors.removeIf(c -> contributorName.equals(c.getName()));
        if (removed) {
            log.info("DefaultPromptContributorManager#unregister - reason=移除 contributor, name={}",
                    contributorName);
        }
    }

    private void sortContributors() {
        // Sort by priority (lower value = higher priority)
        contributors.sort(Comparator.comparingInt(PromptContributor::getPriority));
    }

    private static String concatSystemText(String existing, String next) {
        if (next == null || next.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return next;
        }
        return existing + "\n\n" + next;
    }

    private static void normalizeAndAdd(List<Message> out, List<Message> in) {
        if (in == null || in.isEmpty()) {
            return;
        }
        for (Message message : in) {
            if (message == null) {
                continue;
            }
            if (message instanceof SystemMessage) {
                // Avoid producing multiple SystemMessages in the outgoing message list.
                // Callers should contribute system text via PromptContribution.systemTextTo*
                // instead of returning SystemMessage in message lists.
                continue;
            }
            out.add(message);
        }
    }
}

