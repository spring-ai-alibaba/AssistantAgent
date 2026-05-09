package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assistant Agent Demo Experience Initializer
 *
 * <p>This is an example configuration demonstrating how to initialize experiences.
 *
 * <p>初始化以下 Demo 经验：
 * <ol>
 * <li>COMMON 经验：魔力红是研发助手，魔礼海是研发平台</li>
 * <li>REACT 经验：用户输入"你是谁"时不调用工具，先反问用户</li>
 * <li>REACT 经验：用户想计算"小明系数"时先写代码再反问乘方系数</li>
 * </ol>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@Order(100)
@ConditionalOnProperty(
    prefix = "spring.ai.alibaba.codeact.extension.experience",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DemoExperienceConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoExperienceConfig.class);

    private final ExperienceRepository experienceRepository;

    public DemoExperienceConfig(ExperienceRepository experienceRepository) {
        this.experienceRepository = experienceRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("DemoExperienceConfig#run - reason=Initializing Demo Experiences");

        // 初始化常识经验
        initializeCommonExperienceMoLiHong();
        initializeCommonExperienceMoLiHai();

        // 初始化 React 经验
        initializeReactExperienceWhoAreYou();
        initializeReactExperienceXiaomingCoefficient();

        log.info("DemoExperienceConfig#run - reason=Demo Experiences initialization completed");
    }

    /**
     * 常识经验：魔力红是研发助手
     */
    private void initializeCommonExperienceMoLiHong() {
        Experience experience = new Experience();
        experience.setId("exp-common-molihong-" + UUID.randomUUID().toString().substring(0, 8));
        experience.setType(ExperienceType.COMMON);
        experience.setTitle("魔力红身份介绍");
        experience.setDescription("关于魔力红智能研发助手的身份、定位和核心能力的概念介绍");
        experience.setContent(
            "【魔力红】是一个智能研发助手。\n\n" +
            "魔力红的定位和能力：\n" +
            "1. 魔力红是魔礼海团队开发的 AI 研发助手\n" +
            "2. 专注于帮助开发者解决技术问题和提升研发效率\n" +
            "3. 具备代码生成、代码分析、知识检索、工具调用等能力\n" +
            "4. 可以理解复杂的技术需求并给出专业的解决方案\n\n" +
            "当用户询问'魔力红是什么'或'你是谁'时，应该介绍魔力红是魔礼海团队的 AI 研发助手。"
        );
        experience.setTags(Set.of("魔力红", "身份", "介绍", "助手"));
        experience.setCreatedAt(Instant.now());
        experience.setUpdatedAt(Instant.now());

        experienceRepository.save(experience);
        log.info("DemoExperienceConfig#initializeCommonExperienceMoLiHong - reason=Created MoLiHong identity experience, id={}", experience.getId());
    }

    /**
     * 常识经验：魔礼海是研发平台
     */
    private void initializeCommonExperienceMoLiHai() {
        Experience experience = new Experience();
        experience.setId("exp-common-molihai-" + UUID.randomUUID().toString().substring(0, 8));
        experience.setType(ExperienceType.COMMON);
        experience.setTitle("魔礼海平台介绍");
        experience.setDescription("关于魔礼海智能研发平台的定位、核心能力和与魔力红关系的概念介绍");
        experience.setContent(
            "【魔礼海】是一个智能研发平台。\n\n" +
            "魔礼海的定位和能力：\n" +
            "1. 魔礼海是一个面向企业的智能研发平台\n" +
            "2. 提供 AI 驱动的研发工具链和解决方案\n" +
            "3. 包含代码智能、知识管理、研发协作等核心能力\n" +
            "4. 魔力红是魔礼海平台的核心 AI 助手\n\n" +
            "当用户询问'魔礼海是什么'时，应该介绍魔礼海是一个智能研发平台，而魔力红是该平台的 AI 助手。"
        );
        experience.setTags(Set.of("魔礼海", "平台", "研发", "介绍"));
        experience.setCreatedAt(Instant.now());
        experience.setUpdatedAt(Instant.now());

        experienceRepository.save(experience);
        log.info("DemoExperienceConfig#initializeCommonExperienceMoLiHai - reason=Created MoLiHai platform experience, id={}", experience.getId());
    }

    /**
     * React 经验：用户输入"你是谁"时不调用工具，先反问用户
     */
    private void initializeReactExperienceWhoAreYou() {
        Experience experience = new Experience();
        experience.setId("exp-react-whoareyou-" + UUID.randomUUID().toString().substring(0, 8));
        experience.setType(ExperienceType.REACT);
        experience.setTitle("身份询问响应策略");
        experience.setDescription("当用户询问'你是谁'等身份问题时，直接回复介绍魔力红身份，无需调用工具");
        experience.setContent(
            "【用户询问身份时的响应策略】\n\n" +
            "当用户询问'你是谁'、'你是什么'、'介绍一下你自己'等身份相关问题时：\n\n" +
            "1. 不需要调用任何工具\n" +
            "2. 直接回复用户，介绍自己是魔力红\n" +
            "3. 可以反问用户需要什么帮助\n\n" +
            "示例响应：\n" +
            "'我是魔力红，魔礼海团队的 AI 研发助手。我可以帮助你编写代码、分析问题、查询知识等。请问有什么我可以帮助你的吗？'"
        );
        experience.setTags(Set.of("你是谁", "身份", "介绍", "反问"));
        experience.setCreatedAt(Instant.now());
        experience.setUpdatedAt(Instant.now());

        experienceRepository.save(experience);
        log.info("DemoExperienceConfig#initializeReactExperienceWhoAreYou - reason=Created who-are-you react experience, id={}", experience.getId());
    }

    /**
     * React 经验：小明系数计算策略
     * 设计为多步骤工作流，description 只给简要摘要，content 包含完整的计算规则和分支逻辑，
     * 使得 LLM 必须 read_exp 才能正确执行。
     */
    private void initializeReactExperienceXiaomingCoefficient() {
        Experience experience = new Experience();
        experience.setId("exp-react-xiaoming-" + UUID.randomUUID().toString().substring(0, 8));
        experience.setType(ExperienceType.REACT);
        experience.setTitle("小明系数计算策略");
        experience.setDescription("小明系数是一种自定义的复合运算，包含多步计算规则和特殊边界处理");
        experience.setContent(
            "【小明系数计算的完整策略】\n\n" +
            "小明系数是一种自定义的复合数学运算，不是简单的乘方。其完整计算规则如下：\n\n" +
            "## 计算公式\n" +
            "给定两个参数 a 和 b，小明系数 XM(a, b) 的计算分三步：\n" +
            "1. 第一步：计算基础值 base_val = a * b + (a + b)\n" +
            "2. 第二步：计算调整因子 factor = (a % 7 + b % 7) / 7.0\n" +
            "3. 第三步：最终结果 = round(base_val * (1 + factor), 2)\n\n" +
            "## 特殊规则\n" +
            "- 如果 a 和 b 都是负数，结果取绝对值\n" +
            "- 如果 a 或 b 为 0，直接返回 0，并附带提示「零值参数导致系数为零」\n" +
            "- 如果结果超过 10000，返回结果的同时附带警告「结果较大，请注意数值溢出」\n\n" +
            "## 输出格式\n" +
            "返回字典必须包含以下字段：\n" +
            "- success: True/False\n" +
            "- xiaoming_coefficient: 最终计算结果\n" +
            "- steps: 包含 base_val、factor、final 三个中间值的字典，便于用户验证\n" +
            "- warnings: 警告信息列表（可为空）\n\n" +
            "## 响应策略\n" +
            "1. 使用 write_code 编写完整的计算函数，必须包含所有特殊规则\n" +
            "2. 使用 execute_code 执行计算\n" +
            "3. 将 steps 中间值展示给用户，让用户了解计算过程"
        );
        experience.setTags(Set.of("小明系数", "计算", "复合运算", "自定义公式"));

        // 配置快速意图：匹配用户消息包含"小明系数"时，直接跳过思考和工具调用，进入代码执行
        FastIntentConfig fastIntentConfig = new FastIntentConfig();
        fastIntentConfig.setEnabled(true);
        fastIntentConfig.setPriority(100);

        FastIntentConfig.MatchExpression matchExpression = new FastIntentConfig.MatchExpression();
        FastIntentConfig.Condition condition = new FastIntentConfig.Condition();
        condition.setType("message_regex");
        condition.setPattern(".*小明系数.*");
        matchExpression.setCondition(condition);
        fastIntentConfig.setMatch(matchExpression);

        experience.setFastIntentConfig(fastIntentConfig);

        // 配置快速意图产物：直接调用 write_code 工具执行代码
        ExperienceArtifact artifact = new ExperienceArtifact();
        ExperienceArtifact.ReactArtifact reactArtifact = new ExperienceArtifact.ReactArtifact();
        reactArtifact.setAssistantText("检测到小明系数计算请求，直接执行代码计算。");

        ExperienceArtifact.ToolPlan plan = new ExperienceArtifact.ToolPlan();
        ExperienceArtifact.ToolCallSpec toolCall = new ExperienceArtifact.ToolCallSpec();
        toolCall.setToolName("write_code");
        toolCall.setArguments(Map.of(
            "functionName", "calculate_xiaoming_coefficient",
            "description", "计算小明系数（复合运算），传入参数a和b，按照小明系数公式计算",
            "parameters", List.of("a", "b"),
            "code", String.join("\n", List.of(
                "def calculate_xiaoming_coefficient(a, b):",
                "    warnings = []",
                "    if a == 0 or b == 0:",
                "        return {\"success\": True, \"xiaoming_coefficient\": 0, \"steps\": {}, \"warnings\": [\"零值参数导致系数为零\"]}",
                "    base_val = a * b + (a + b)",
                "    factor = (a % 7 + b % 7) / 7.0",
                "    result = round(base_val * (1 + factor), 2)",
                "    if a < 0 and b < 0:",
                "        result = abs(result)",
                "    if result > 10000:",
                "        warnings.append(\"结果较大，请注意数值溢出\")",
                "    return {\"success\": True, \"xiaoming_coefficient\": result, \"steps\": {\"base_val\": base_val, \"factor\": round(factor, 4), \"final\": result}, \"warnings\": warnings}"
            ))
        ));
        plan.setToolCalls(List.of(toolCall));
        reactArtifact.setPlan(plan);
        artifact.setReact(reactArtifact);
        experience.setArtifact(artifact);

        experience.setCreatedAt(Instant.now());
        experience.setUpdatedAt(Instant.now());

        experienceRepository.save(experience);
        log.info("DemoExperienceConfig#initializeReactExperienceXiaomingCoefficient - reason=Created xiaoming coefficient react experience with fast intent, id={}", experience.getId());
    }
}
