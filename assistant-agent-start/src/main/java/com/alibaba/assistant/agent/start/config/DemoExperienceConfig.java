package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceScope;
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
 * <li>CODE 经验：小明系数就是乘方，内部以乘方方式实现</li>
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
/*        initializeCommonExperienceMoLiHong();
        initializeCommonExperienceMoLiHai();

        // 初始化 React 经验
        initializeReactExperienceWhoAreYou();
        initializeReactExperienceXiaomingCoefficient();

        // 初始化 Code 经验
        initializeCodeExperienceXiaomingCoefficient();*/

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
        experience.setContent(
            "【魔力红】是一个智能研发助手。\n\n" +
            "魔力红的定位和能力：\n" +
            "1. 魔力红是魔礼海团队开发的 AI 研发助手\n" +
            "2. 专注于帮助开发者解决技术问题和提升研发效率\n" +
            "3. 具备代码生成、代码分析、知识检索、工具调用等能力\n" +
            "4. 可以理解复杂的技术需求并给出专业的解决方案\n\n" +
            "当用户询问'魔力红是什么'或'你是谁'时，应该介绍魔力红是魔礼海团队的 AI 研发助手。"
        );
        experience.setScope(ExperienceScope.GLOBAL);
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
        experience.setContent(
            "【魔礼海】是一个智能研发平台。\n\n" +
            "魔礼海的定位和能力：\n" +
            "1. 魔礼海是一个面向企业的智能研发平台\n" +
            "2. 提供 AI 驱动的研发工具链和解决方案\n" +
            "3. 包含代码智能、知识管理、研发协作等核心能力\n" +
            "4. 魔力红是魔礼海平台的核心 AI 助手\n\n" +
            "当用户询问'魔礼海是什么'时，应该介绍魔礼海是一个智能研发平台，而魔力红是该平台的 AI 助手。"
        );
        experience.setScope(ExperienceScope.GLOBAL);
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
        experience.setContent(
            "【用户询问身份时的响应策略】\n\n" +
            "当用户询问'你是谁'、'你是什么'、'介绍一下你自己'等身份相关问题时：\n\n" +
            "1. 不需要调用任何工具\n" +
            "2. 直接回复用户，介绍自己是魔力红\n" +
            "3. 可以反问用户需要什么帮助\n\n" +
            "示例响应：\n" +
            "'我是魔力红，魔礼海团队的 AI 研发助手。我可以帮助你编写代码、分析问题、查询知识等。请问有什么我可以帮助你的吗？'"
        );
        experience.setScope(ExperienceScope.GLOBAL);
        experience.setTags(Set.of("你是谁", "身份", "介绍", "反问"));
        experience.setCreatedAt(Instant.now());
        experience.setUpdatedAt(Instant.now());

        experienceRepository.save(experience);
        log.info("DemoExperienceConfig#initializeReactExperienceWhoAreYou - reason=Created who-are-you react experience, id={}", experience.getId());
    }

    /**
     * React 经验：用户想计算小明系数时的响应策略
     */
    private void initializeReactExperienceXiaomingCoefficient() {
        Experience experience = new Experience();
        experience.setId("exp-react-xiaoming-" + UUID.randomUUID().toString().substring(0, 8));
        experience.setType(ExperienceType.REACT);
        experience.setTitle("小明系数计算策略");
        experience.setContent(
            "【小明系数计算的响应策略】\n\n" +
            "当用户想要计算'小明系数'时：\n\n" +
            "1. 首先编写一个计算小明系数的代码框架\n" +
            "2. 然后反问用户需要计算的乘方系数是多少\n" +
            "3. 等用户提供系数后再执行计算\n\n" +
            "示例响应：\n" +
            "'我已经准备好计算小明系数的代码了。小明系数本质上是乘方运算，请问你需要计算多少次方？例如：2次方（平方）、3次方（立方）等。'"
        );
        experience.setScope(ExperienceScope.GLOBAL);
        experience.setTags(Set.of("小明系数", "计算", "乘方", "反问"));

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
            "requirement", "计算小明系数（乘方运算），传入底数base和指数exponent，返回base的exponent次方",
            "functionName", "calculate_xiaoming_coefficient",
            "parameters", List.of("base", "exponent")
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

    /**
     * Code 经验：小明系数的代码实现
     * <p>
     * 只需配置 artifact.code，content 会通过 Experience.getEffectiveContent() 自动生成
     * - 快速意图阶段：使用 artifact.code 跳过 LLM 代码生成
     * - 评估/Prompt 注入阶段：使用 getEffectiveContent() 自动从 artifact.code 生成内容
     */
    private void initializeCodeExperienceXiaomingCoefficient() {
        Experience experience = new Experience();
        experience.setId("exp-code-xiaoming-" + UUID.randomUUID().toString().substring(0, 8));
        experience.setType(ExperienceType.CODE);
        experience.setTitle("小明系数代码实现");
        // 不需要手动设置 content，会从 artifact.code 自动生成
        experience.setScope(ExperienceScope.GLOBAL);
        experience.setLanguage("python");
        experience.setTags(Set.of("小明系数", "乘方", "代码", "python"));

        // 配置快速意图
        FastIntentConfig fastIntentConfig = new FastIntentConfig();
        fastIntentConfig.setEnabled(true);
        fastIntentConfig.setPriority(90);

        // 匹配规则：通过 tool_arg_equals 匹配 functionName
        FastIntentConfig.MatchExpression matchExpression = new FastIntentConfig.MatchExpression();
        FastIntentConfig.Condition condition = new FastIntentConfig.Condition();
        condition.setType("tool_arg_equals");
        condition.setKey("functionName");
        condition.setValue("calculate_xiaoming_coefficient");
        matchExpression.setCondition(condition);
        fastIntentConfig.setMatch(matchExpression);

        experience.setFastIntentConfig(fastIntentConfig);

        // 配置 artifact.code：这是唯一的代码数据源
        ExperienceArtifact artifact = new ExperienceArtifact();
        ExperienceArtifact.CodeArtifact code = new ExperienceArtifact.CodeArtifact();
        code.setLanguage("python");
        code.setFunctionName("calculate_xiaoming_coefficient");
        code.setParameters(List.of("base", "exponent"));
        code.setDescription("小明系数本质上就是乘方运算，使用 ** 运算符实现");
        code.setCode(
            "def calculate_xiaoming_coefficient(base: float, exponent: int) -> float:\n" +
            "    \"\"\"\n" +
            "    计算小明系数（乘方运算）\n" +
            "    \n" +
            "    Args:\n" +
            "        base: 底数\n" +
            "        exponent: 指数（乘方系数）\n" +
            "    \n" +
            "    Returns:\n" +
            "        小明系数计算结果\n" +
            "    \"\"\"\n" +
            "    return base ** exponent\n"
        );
        artifact.setCode(code);
        experience.setArtifact(artifact);

        experience.setCreatedAt(Instant.now());
        experience.setUpdatedAt(Instant.now());

        experienceRepository.save(experience);
        log.info("DemoExperienceConfig#initializeCodeExperienceXiaomingCoefficient - reason=Created xiaoming coefficient code experience with fast intent, id={}", experience.getId());
    }
}

