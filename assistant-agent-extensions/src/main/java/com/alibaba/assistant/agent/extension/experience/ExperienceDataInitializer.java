package com.alibaba.assistant.agent.extension.experience;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;
import com.alibaba.assistant.agent.extension.experience.model.*;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 经验模块示例数据初始化器
 * 用于演示和测试，在实际生产环境中应该禁用
 *
 * @author Assistant Agent Team
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience.demo",
                      name = "enabled",
                      havingValue = "true",
                      matchIfMissing = false)
public class ExperienceDataInitializer implements CommandLineRunner {

    private final ExperienceRepository experienceRepository;

    public ExperienceDataInitializer(ExperienceRepository experienceRepository) {
        this.experienceRepository = experienceRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        initializeDemoData();
    }

    private void initializeDemoData() {
        // 初始化React经验（包含原CODE经验迁移后的数据）
        initializeReactExperiences();

        // 初始化常识经验
        initializeCommonExperiences();
    }

    private void initializeReactExperiences() {
        // === 原 CODE 经验迁移为 REACT 类型 ===

        // Java日志规范经验（原CODE类型，迁移为REACT）
        Experience javaLoggingExp = new Experience(
                ExperienceType.REACT,
                "Java日志格式规范",
                "按照要求，日志格式应该为：类名 + 目标方法 + 打印原因\n\n" +
                "```java\n" +
                "private static final Logger log = LoggerFactory.getLogger(ClassName.class);\n\n" +
                "public void methodName() {\n" +
                "    log.info(\"ClassName#methodName - reason=具体原因描述\");\n" +
                "}\n" +
                "```\n\n" +
                "这样的格式便于日志分析和问题排查。"
        );
        javaLoggingExp.setTags(Set.of("logging", "format", "standard"));
        javaLoggingExp.getMetadata().setSource("manual");
        javaLoggingExp.getMetadata().setConfidence(1.0);
        javaLoggingExp.setDisclosureStrategy(DisclosureStrategy.PROGRESSIVE);

        // Spring Boot异常处理经验（原CODE类型，迁移为REACT）
        Experience springExceptionExp = new Experience(
                ExperienceType.REACT,
                "Spring Boot统一异常处理",
                "使用@ControllerAdvice进行全局异常处理：\n\n" +
                "```java\n" +
                "@ControllerAdvice\n" +
                "public class GlobalExceptionHandler {\n" +
                "    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);\n\n" +
                "    @ExceptionHandler(Exception.class)\n" +
                "    public ResponseEntity<ErrorResponse> handleException(Exception e) {\n" +
                "        log.error(\"GlobalExceptionHandler#handleException - reason=unhandled exception occurred\", e);\n" +
                "        return ResponseEntity.status(500).body(new ErrorResponse(\"Internal Server Error\"));\n" +
                "    }\n" +
                "}\n" +
                "```"
        );
        springExceptionExp.getMetadata().setTenantIdList(List.of("demo-team"));
        springExceptionExp.setTags(Set.of("spring-boot", "exception-handling", "controller-advice"));
        springExceptionExp.getMetadata().setSource("manual");
        springExceptionExp.getMetadata().setConfidence(0.9);
        springExceptionExp.setDisclosureStrategy(DisclosureStrategy.PROGRESSIVE);

        experienceRepository.save(javaLoggingExp);
        experienceRepository.save(springExceptionExp);

        // FastIntent example: 原CODE经验迁移为REACT + write_code ToolCallSpec
        Experience fastWriteCode = new Experience(
                ExperienceType.REACT,
                "FastIntent: tenant=demo direct function execution",
                "When tenant=demo, skip LLM code generation and directly execute handle_demo_request via write_code."
        );
        fastWriteCode.getMetadata().setTenantIdList(List.of("demo"));
        fastWriteCode.setTags(Set.of("fast-intent", "demo"));
        fastWriteCode.setDisclosureStrategy(DisclosureStrategy.DIRECT);

        FastIntentConfig cfg = new FastIntentConfig();
        cfg.setEnabled(true);
        cfg.setPriority(500);
        FastIntentConfig.Condition cond = new FastIntentConfig.Condition();
        cond.setType("metadata_equals");
        cond.setKey("tenant");
        cond.setValue("demo");
        FastIntentConfig.MatchExpression match = new FastIntentConfig.MatchExpression();
        match.setCondition(cond);
        cfg.setMatch(match);
        fastWriteCode.setFastIntentConfig(cfg);

        // 使用 ReactArtifact + write_code ToolCallSpec 替代原 CodeArtifact
        ExperienceArtifact artifact = new ExperienceArtifact();
        ExperienceArtifact.ReactArtifact reactArtifact = new ExperienceArtifact.ReactArtifact();
        ExperienceArtifact.ToolPlan plan = new ExperienceArtifact.ToolPlan();
        ExperienceArtifact.ToolCallSpec writeCodeCall = new ExperienceArtifact.ToolCallSpec();
        writeCodeCall.setToolName("write_code");
        writeCodeCall.setArguments(java.util.Map.of(
                "functionName", "handle_demo_request",
                "description", "Handle demo request",
                "parameters", List.of(),
                "code", "def handle_demo_request():\n    return 'I am MoLiHong'\n"
        ));
        plan.setToolCalls(List.of(writeCodeCall));
        reactArtifact.setPlan(plan);
        artifact.setReact(reactArtifact);
        fastWriteCode.setArtifact(artifact);

        experienceRepository.save(fastWriteCode);

        // === 原有 REACT 经验 ===
        // 代码生成策略经验
        Experience codeGenExp = new Experience(
                ExperienceType.REACT,
                "代码生成最佳实践",
                "在进行代码生成时，建议遵循以下步骤：\n\n" +
                "1. 首先分析用户需求，明确要实现的功能\n" +
                "2. 查看当前项目结构，了解现有代码风格\n" +
                "3. 选择合适的设计模式和架构\n" +
                "4. 生成代码时保持与项目风格一致\n" +
                "5. 添加适当的注释和文档\n" +
                "6. 考虑异常处理和边界情况\n\n" +
                "优先使用项目中已有的工具类和框架，避免重复造轮子。"
        );
        codeGenExp.getMetadata().setTenantIdList(List.of("demo-team"));
        codeGenExp.setTags(Set.of("code-generation", "best-practice", "strategy"));
        codeGenExp.getMetadata().setSource("learned");
        codeGenExp.getMetadata().setConfidence(0.8);

        // 工具选择经验
        Experience toolSelectionExp = new Experience(
                ExperienceType.REACT,
                "工具选择策略",
                "根据任务类型选择合适的工具：\n\n" +
                "- 文件操作：优先使用read_file、write_file工具\n" +
                "- 代码分析：使用analyze_code工具\n" +
                "- 项目构建：使用build_project工具\n" +
                "- 测试执行：使用run_tests工具\n\n" +
                "在使用工具前，先检查必要的参数是否齐全，避免工具调用失败。"
        );
        toolSelectionExp.setTags(Set.of("tool-selection", "strategy", "efficiency"));
        toolSelectionExp.getMetadata().setSource("learned");
        toolSelectionExp.getMetadata().setConfidence(0.85);

        experienceRepository.save(codeGenExp);
        experienceRepository.save(toolSelectionExp);

        // FastIntent 示例：REACT 经验（命中后跳过本轮 model，直接进入 tool 执行）
        Experience fastReact = new Experience(
                ExperienceType.REACT,
                "FastIntent: 前缀 ping 走固定工具链",
                "当用户以 ping 开头时，直接执行固定工具链。"
        );
        fastReact.setTags(Set.of("fast-intent", "react", "demo"));

        FastIntentConfig rCfg = new FastIntentConfig();
        rCfg.setEnabled(true);
        rCfg.setPriority(1000);
        FastIntentConfig.Condition rCond = new FastIntentConfig.Condition();
        rCond.setType("message_prefix");
        rCond.setValue("ping");
        FastIntentConfig.MatchExpression rMatch = new FastIntentConfig.MatchExpression();
        rMatch.setCondition(rCond);
        rCfg.setMatch(rMatch);
        fastReact.setFastIntentConfig(rCfg);

        ExperienceArtifact rArtifact = new ExperienceArtifact();
        ExperienceArtifact.ReactArtifact fastReactArtifact = new ExperienceArtifact.ReactArtifact();
        fastReactArtifact.setAssistantText("我将直接执行固定工具链。");
        ExperienceArtifact.ToolPlan fastPlan = new ExperienceArtifact.ToolPlan();
        ExperienceArtifact.ToolCallSpec call1 = new ExperienceArtifact.ToolCallSpec();
        call1.setToolName("reply");
        call1.setArguments(java.util.Map.of("content", "pong"));
        fastPlan.setToolCalls(List.of(call1));
        fastReactArtifact.setPlan(fastPlan);
        rArtifact.setReact(fastReactArtifact);
        fastReact.setArtifact(rArtifact);

        experienceRepository.save(fastReact);
    }

    private void initializeCommonExperiences() {
        // 安全规范
        Experience securityExp = new Experience(
                ExperienceType.COMMON,
                "代码安全规范",
                "在编写代码时必须遵循以下安全规范：\n\n" +
                "1. 不要在代码或日志中写入明文密码、API密钥等敏感信息\n" +
                "2. 对用户输入进行验证和过滤，防止注入攻击\n" +
                "3. 使用参数化查询，避免SQL注入\n" +
                "4. 及时更新依赖库，修复已知安全漏洞\n" +
                "5. 对敏感操作添加权限检查\n\n" +
                "这些规范是强制性的，不得违反。"
        );
        securityExp.setTags(Set.of("security", "mandatory", "best-practice"));
        securityExp.getMetadata().setSource("policy");
        securityExp.getMetadata().setConfidence(1.0);

        // 代码质量规范
        Experience qualityExp = new Experience(
                ExperienceType.COMMON,
                "代码质量要求",
                "代码质量要求如下：\n\n" +
                "1. 遵循统一的代码格式和命名规范\n" +
                "2. 方法长度不超过50行，类长度不超过500行\n" +
                "3. 复杂方法必须添加注释说明\n" +
                "4. 单元测试覆盖率不低于70%\n" +
                "5. 避免代码重复，提取公共逻辑\n" +
                "6. 日志格式统一为：类名 + 目标方法 + 打印原因\n\n" +
                "请在代码生成时严格遵循这些要求。"
        );
        qualityExp.setTags(Set.of("code-quality", "standards", "mandatory"));
        qualityExp.getMetadata().setSource("policy");
        qualityExp.getMetadata().setConfidence(1.0);

        experienceRepository.save(securityExp);
        experienceRepository.save(qualityExp);
    }
}
