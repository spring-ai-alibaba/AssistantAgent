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
package com.alibaba.assistant.agent.start.search;

import com.alibaba.assistant.agent.extension.search.model.SearchRequest;
import com.alibaba.assistant.agent.extension.search.model.SearchResultItem;
import com.alibaba.assistant.agent.extension.search.model.SearchSourceType;
import com.alibaba.assistant.agent.extension.search.spi.SearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mock 知识库搜索Provider（基于Store/经验池）
 * 提供简单的模拟实现用于演示和测试
 *
 * @author Assistant Agent Team
 */
@Component
public class MockKnowledgeStoreSearchProvider implements SearchProvider {

    private static final Logger logger = LoggerFactory.getLogger(MockKnowledgeStoreSearchProvider.class);

    // Demo 知识库数据
    private static final List<KnowledgeItem> DEMO_KNOWLEDGE = List.of(
            new KnowledgeItem(
                    "Spring AI Alibaba 快速开始",
                    "Spring AI Alibaba 是阿里云提供的 Spring AI 实现，支持通义千问、百炼等模型。",
                    "# Spring AI Alibaba 快速开始\n\n" +
                            "Spring AI Alibaba 是基于 Spring AI 框架的阿里云实现，提供以下核心能力：\n\n" +
                            "1. **模型支持**: 通义千问(Qwen)系列模型，包括 qwen-max, qwen-plus, qwen-turbo 等\n" +
                            "2. **多模态**: 支持文本、图像、语音等多模态交互\n" +
                            "3. **函数调用**: 原生支持 Function Calling 和 Tool Use\n" +
                            "4. **向量存储**: 集成阿里云向量数据库\n\n" +
                            "## Maven 依赖\n```xml\n<dependency>\n    <groupId>com.alibaba.cloud.ai</groupId>\n    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>\n</dependency>\n```\n\n" +
                            "## 配置示例\n```yaml\nspring:\n  ai:\n    dashscope:\n      api-key: ${DASHSCOPE_API_KEY}\n      chat:\n        options:\n          model: qwen-max\n```",
                    List.of("spring-ai", "阿里云", "通义千问", "快速开始")
            ),
            new KnowledgeItem(
                    "灰度发布最佳实践",
                    "灰度发布是一种平滑上线的策略，通过逐步放量来降低发布风险。",
                    "# 灰度发布最佳实践\n\n" +
                            "## 什么是灰度发布\n" +
                            "灰度发布（Gray Release）是一种平滑过渡的发布策略，通过控制流量比例，让新版本逐步替换旧版本。\n\n" +
                            "## 核心概念\n" +
                            "1. **灰度规则**: 基于租户ID、用户ID、地域等维度配置流量规则\n" +
                            "2. **灰度环境**: 通常包括 prepub(预发)、gray(灰度)、prod(生产)\n" +
                            "3. **流量控制**: 通过百分比或白名单控制新版本流量\n\n" +
                            "## 灰度配置查询\n" +
                            "使用 ConfigTools.query_grayscale 方法查询灰度配置：\n" +
                            "```python\n" +
                            "result = agent_tools.call('query_grayscale', json.dumps({\n" +
                            "    'tenant_id': '12345',\n" +
                            "    'grayscale_key': 'feature.new_ui',\n" +
                            "    'environment': 'prepub'\n" +
                            "}))\n```\n\n" +
                            "## 最佳实践\n" +
                            "1. 先在预发环境验证\n" +
                            "2. 小流量灰度（1%-5%）\n" +
                            "3. 监控核心指标\n" +
                            "4. 逐步扩大流量\n" +
                            "5. 全量后保持观察",
                    List.of("灰度发布", "发布策略", "流量控制", "最佳实践")
            ),
            new KnowledgeItem(
                    "Codeact 智能体架构设计",
                    "Codeact 是一个支持代码生成和执行的 AI 智能体框架，基于 ReAct 模式。",
                    "# Codeact 智能体架构设计\n\n" +
                            "## 核心能力\n" +
                            "Codeact Agent 是一个创新的智能体架构，具有以下特点：\n\n" +
                            "1. **动态代码生成**: 根据用户意图生成 Python/JavaScript 代码\n" +
                            "2. **安全执行**: 基于 GraalVM 沙箱环境执行代码\n" +
                            "3. **工具调用**: 支持注册和调用外部工具（ExecutableTool）\n" +
                            "4. **多轮对话**: 支持 ReAct 循环，可多次思考和行动\n\n" +
                            "## 架构组件\n" +
                            "- **CodeactAgent**: 核心智能体类\n" +
                            "- **WriteCodeTool**: 代码生成工具\n" +
                            "- **ExecuteCodeTool**: 代码执行工具\n" +
                            "- **ExecutableToolRegistry**: 可执行工具注册表\n" +
                            "- **GraalCodeExecutor**: 基于 GraalVM 的代码执行器\n\n" +
                            "## 使用示例\n" +
                            "```java\n" +
                            "CodeactAgent agent = CodeactAgent.builder()\n" +
                            "    .name(\"MyAgent\")\n" +
                            "    .model(chatModel)\n" +
                            "    .language(Language.PYTHON)\n" +
                            "    .executableTool(new MyCustomTool())\n" +
                            "    .build();\n\n" +
                            "String result = agent.call(\"查询租户123的灰度配置\");\n" +
                            "```\n\n" +
                            "## 扩展机制\n" +
                            "- **Hook 机制**: 支持在各个阶段注入自定义逻辑\n" +
                            "- **Experience 经验**: 可积累和复用历史经验\n" +
                            "- **Learning 学习**: 支持在线学习优化能力",
                    List.of("codeact", "智能体", "架构设计", "代码生成", "react")
            ),
            new KnowledgeItem(
                    "Python 代码执行安全机制",
                    "在 AI 智能体中执行用户生成的代码需要严格的安全控制。",
                    "# Python 代码执行安全机制\n\n" +
                            "## 安全挑战\n" +
                            "AI 生成的代码可能包含：\n" +
                            "1. 文件系统访问\n" +
                            "2. 网络请求\n" +
                            "3. 系统命令执行\n" +
                            "4. 资源消耗攻击\n\n" +
                            "## 安全策略\n\n" +
                            "### 1. 沙箱隔离\n" +
                            "使用 GraalVM Polyglot 提供的沙箱环境：\n" +
                            "```java\n" +
                            "Context context = Context.newBuilder(\"python\")\n" +
                            "    .allowIO(false)  // 禁止 IO 操作\n" +
                            "    .allowNativeAccess(false)  // 禁止本地访问\n" +
                            "    .option(\"engine.WarnInterpreterOnly\", \"false\")\n" +
                            "    .build();\n" +
                            "```\n\n" +
                            "### 2. 执行超时\n" +
                            "```java\n" +
                            "context.eval(Source.create(\"python\", code))\n" +
                            "    .executeVoid();\n" +
                            "// 配置 30 秒超时\n" +
                            "```\n\n" +
                            "### 3. 白名单机制\n" +
                            "只允许调用注册的 ExecutableTool：\n" +
                            "- ConfigTools.query_grayscale\n" +
                            "- SearchTools.unified_search\n" +
                            "- 等等\n\n" +
                            "### 4. 代码审查\n" +
                            "在执行前检查代码中的危险操作",
                    List.of("python", "安全", "沙箱", "代码执行", "graalvm")
            ),
            new KnowledgeItem(
                    "DevOps 平台",
                    "统一的 DevOps 平台，提供项目管理、代码托管和 CI/CD 能力。",
                    "# DevOps 平台\n\n" +
                            "## 平台概述\n" +
                            "一个综合的 DevOps 平台，集成了：\n" +
                            "- 项目管理（需求、任务、缺陷）\n" +
                            "- 代码托管（Git）\n" +
                            "- 持续集成/部署（CI/CD）\n" +
                            "- 测试管理\n" +
                            "- 发布管理\n\n" +
                            "## 核心功能\n\n" +
                            "### 1. 项目管理\n" +
                            "- 敏捷看板\n" +
                            "- 迭代规划\n" +
                            "- 需求跟踪\n\n" +
                            "### 2. 代码管理\n" +
                            "- 基于 Git 的代码托管\n" +
                            "- 代码评审流程\n" +
                            "- 分支管理\n\n" +
                            "### 3. 构建与发布\n" +
                            "- 自动化构建\n" +
                            "- 多环境部署\n" +
                            "- 灰度发布支持\n\n" +
                            "### 4. 质量控制\n" +
                            "- 自动化测试\n" +
                            "- 代码扫描\n" +
                            "- 质量门禁\n\n" +
                            "## 与 AI 助手集成\n" +
                            "AI 助手可以通过 API 访问平台数据：\n" +
                            "- 查询项目信息\n" +
                            "- 获取构建状态\n" +
                            "- 查看发布记录\n" +
                            "- 检查代码质量",
                    List.of("devops", "平台", "项目管理", "ci/cd")
            ),
            new KnowledgeItem(
                    "魔力红 AI 助手",
                    "魔力红是基于 Spring AI Alibaba 和 Codeact 框架构建的智能开发助手。",
                    "# 魔力红 AI 助手\n\n" +
                            "## 身份\n" +
                            "魔力红是由魔力海(Assistant AI Team)创建的 AI 驱动开发助手，专注于：\n" +
                            "- 配置查询\n" +
                            "- 信息检索\n" +
                            "- 开发问答\n" +
                            "- 自动化任务执行\n\n" +
                            "## 技术架构\n" +
                            "- **框架**: Spring AI Alibaba\n" +
                            "- **智能体**: Codeact Agent\n" +
                            "- **模型**: 通义千问系列\n" +
                            "- **代码执行**: GraalVM Python\n\n" +
                            "## 核心能力\n\n" +
                            "### 1. 配置查询\n" +
                            "```\n" +
                            "用户：查询租户 12345 的 feature.new_ui 配置\n" +
                            "魔力红：[生成并执行代码] 当前状态：已启用，流量比例 10%\n" +
                            "```\n\n" +
                            "### 2. 知识问答\n" +
                            "基于知识库回答开发问题\n\n" +
                            "### 3. 代码生成\n" +
                            "根据需求生成并执行代码\n\n" +
                            "## 使用场景\n" +
                            "1. 快速配置查询\n" +
                            "2. 平台使用指导\n" +
                            "3. 自动化数据处理\n" +
                            "4. 开发流程咨询",
                    List.of("魔力红", "ai助手", "智能体", "intelligent-agent")
            )
    );

    @Override
    public boolean supports(SearchSourceType type) {
        return SearchSourceType.KNOWLEDGE == type;
    }

    @Override
    public List<SearchResultItem> search(SearchRequest request) {
        logger.info("MockKnowledgeStoreSearchProvider#search - reason=execute knowledge search with query={}", request.getQuery());

        List<SearchResultItem> results = new ArrayList<>();

        try {
            int topK = request.getPerSourceTopK().getOrDefault(SearchSourceType.KNOWLEDGE, request.getTopK());
            String query = request.getQuery().toLowerCase();

            // 简单的关键词匹配搜索
            for (KnowledgeItem knowledge : DEMO_KNOWLEDGE) {
                double score = calculateRelevanceScore(query, knowledge);
                if (score > 0.1) { // 相关度阈值
                    SearchResultItem item = new SearchResultItem();
                    item.setId(UUID.randomUUID().toString());
                    item.setSourceType(SearchSourceType.KNOWLEDGE);
                    item.setTitle(knowledge.title);
                    item.setSnippet(knowledge.snippet);
                    item.setContent(knowledge.content);
                    item.setUri("knowledge://doc/" + knowledge.title.hashCode());
                    item.setScore(score);
                    item.getMetadata().setSourceName(getName());
                    item.getMetadata().getExtensions().put("tags", String.join(", ", knowledge.tags));
                    results.add(item);
                }
            }

            // 按相关度排序并截取 topK
            results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            if (results.size() > topK) {
                results = results.subList(0, topK);
            }

            logger.info("MockKnowledgeStoreSearchProvider#search - reason=found {} results", results.size());
        } catch (Exception e) {
            logger.error("MockKnowledgeStoreSearchProvider#search - reason=search failed with error", e);
        }

        return results;
    }

    /**
     * 计算查询与知识项的相关度得分
     */
    private double calculateRelevanceScore(String query, KnowledgeItem knowledge) {
        double score = 0.0;

        // 标题匹配（权重 0.4）
        if (knowledge.title.toLowerCase().contains(query)) {
            score += 0.4;
        }

        // 摘要匹配（权重 0.3）
        if (knowledge.snippet.toLowerCase().contains(query)) {
            score += 0.3;
        }

        // 标签匹配（权重 0.2）
        for (String tag : knowledge.tags) {
            if (tag.toLowerCase().contains(query) || query.contains(tag.toLowerCase())) {
                score += 0.2;
                break;
            }
        }

        // 内容匹配（权重 0.1）
        if (knowledge.content.toLowerCase().contains(query)) {
            score += 0.1;
        }

        return Math.min(score, 1.0);
    }

    @Override
    public String getName() {
        return "MockKnowledgeStoreSearchProvider";
    }

    /**
     * 知识项数据结构
     */
    private static class KnowledgeItem {
        final String title;
        final String snippet;
        final String content;
        final List<String> tags;

        KnowledgeItem(String title, String snippet, String content, List<String> tags) {
            this.title = title;
            this.snippet = snippet;
            this.content = content;
            this.tags = tags;
        }
    }
}

