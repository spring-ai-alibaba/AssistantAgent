package com.alibaba.assistant.agent.extension.experience.config;

import com.alibaba.assistant.agent.extension.experience.hook.CodeExperienceModelHook;
import com.alibaba.assistant.agent.extension.experience.hook.CommonSenseExperienceModelHook;
import com.alibaba.assistant.agent.extension.experience.hook.FastIntentReactHook;
import com.alibaba.assistant.agent.extension.experience.hook.ReactExperienceAgentHook;
import com.alibaba.assistant.agent.extension.experience.tool.CommonSenseInjectionTool;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentService;
import com.alibaba.assistant.agent.extension.experience.internal.InMemoryExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.internal.InMemoryExperienceRepository;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentConditionMatcher;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 经验模块自动配置类
 *
 * @author Assistant Agent Team
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExperienceExtensionProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
                      name = "enabled",
                      havingValue = "true",
                      matchIfMissing = true)
public class ExperienceExtensionAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExperienceExtensionAutoConfiguration.class);

    /**
     * 配置InMemory经验仓库实现
     */
    @Bean
    @ConditionalOnMissingBean(ExperienceRepository.class)
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience.in-memory",
                          name = "enabled",
                          havingValue = "true",
                          matchIfMissing = true)
    public ExperienceRepository inMemoryExperienceRepository() {
        log.info("ExperienceExtensionAutoConfiguration#inMemoryExperienceRepository - reason=creating InMemory experience repository bean");
        return new InMemoryExperienceRepository();
    }

    /**
     * 配置InMemory经验提供者实现
     */
    @Bean
    @ConditionalOnMissingBean(ExperienceProvider.class)
    public ExperienceProvider experienceProvider(ExperienceRepository experienceRepository) {
        log.info("ExperienceExtensionAutoConfiguration#experienceProvider - reason=creating experience provider bean with repository type={}",
                experienceRepository.getClass().getSimpleName());
        return new InMemoryExperienceProvider(experienceRepository);
    }

    @Bean
    @ConditionalOnMissingBean(FastIntentService.class)
    public FastIntentService fastIntentService(ObjectProvider<List<FastIntentConditionMatcher>> matchersProvider) {
        List<FastIntentConditionMatcher> matchers = matchersProvider.getIfAvailable(() -> List.of());
        log.info("ExperienceExtensionAutoConfiguration#fastIntentService - reason=creating FastIntentService, extraMatchers={}",
                matchers != null ? matchers.size() : 0);
        return new FastIntentService(matchers);
    }

    /**
     * FastIntent React Hook（BEFORE_AGENT）
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
                          name = "react-experience-enabled",
                          havingValue = "true",
                          matchIfMissing = true)
    public FastIntentReactHook fastIntentReactHook(ExperienceProvider experienceProvider,
                                                   ExperienceExtensionProperties properties,
                                                   FastIntentService fastIntentService) {
        log.info("ExperienceExtensionAutoConfiguration#fastIntentReactHook - reason=creating fast intent react hook bean");
        return new FastIntentReactHook(experienceProvider, properties, fastIntentService);
    }

    /**
     * 配置代码经验模型Hook
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
                          name = "code-experience-enabled",
                          havingValue = "true",
                          matchIfMissing = true)
    public CodeExperienceModelHook codeExperienceModelHook(ExperienceProvider experienceProvider,
                                                          ExperienceExtensionProperties properties) {
        log.info("ExperienceExtensionAutoConfiguration#codeExperienceModelHook - reason=creating code experience model hook bean");
        return new CodeExperienceModelHook(experienceProvider, properties);
    }

    /**
     * 配置React经验Agent Hook
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
                          name = "react-experience-enabled",
                          havingValue = "true",
                          matchIfMissing = true)
    public ReactExperienceAgentHook reactExperienceAgentHook(ExperienceProvider experienceProvider,
                                                            ExperienceExtensionProperties properties) {
        log.info("ExperienceExtensionAutoConfiguration#reactExperienceAgentHook - reason=creating react experience agent hook bean");
        return new ReactExperienceAgentHook(experienceProvider, properties);
    }

    /**
     * 配置常识经验提示模型Hook
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
                          name = "common-experience-enabled",
                          havingValue = "true",
                          matchIfMissing = true)
    public CommonSenseExperienceModelHook commonSenseExperienceModelHook(ExperienceProvider experienceProvider,
                                                                         ExperienceExtensionProperties properties) {
        log.info("ExperienceExtensionAutoConfiguration#commonSenseExperienceModelHook - reason=creating common sense prompt model hook bean");
        return new CommonSenseExperienceModelHook(experienceProvider, properties);
    }

    /**
     * 配置常识经验注入假工具
     *
     * <p>这个工具与 CommonSenseExperienceModelHook 配套使用。
     * Hook 会注入 AssistantMessage + ToolResponseMessage 配对来模拟工具调用，
     * 注册这个工具可以让 ReactAgent 的路由逻辑正确识别和处理。
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
                          name = "common-experience-enabled",
                          havingValue = "true",
                          matchIfMissing = true)
    public CommonSenseInjectionTool commonSenseInjectionTool() {
        log.info("ExperienceExtensionAutoConfiguration#commonSenseInjectionTool - reason=creating common sense injection tool bean");
        return new CommonSenseInjectionTool();
    }
}
