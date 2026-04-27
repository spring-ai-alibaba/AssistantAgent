package com.alibaba.assistant.agent.extension.experience.config;

import com.alibaba.assistant.agent.extension.experience.disclosure.DefaultExperienceDisclosureContextResolver;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosureContextResolver;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePromptContributor;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosureService;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperiencePrefetchHook;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceRuntimeModelInterceptor;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceRuntimeToolStateInterceptor;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceToolInvocationClassifier;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-configures the progressive disclosure runtime for the experience extension.
 *
 * <p>This configuration is intentionally separated from the older experience hook
 * auto-config so business applications can adopt the disclosure runtime without being
 * forced to re-enable the legacy COMMON/REACT prompt-injection path.
 *
 * <p>Must load after {@link ExperienceExtensionAutoConfiguration} so that
 * {@link ExperienceProvider} and {@link ExperienceRepository} beans are already
 * available via standard Spring DI.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExperienceExtensionProperties.class)
@AutoConfigureAfter(ExperienceExtensionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ExperienceDisclosureAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExperienceDisclosureAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ExperienceDisclosureContextResolver.class)
    public ExperienceDisclosureContextResolver experienceDisclosureContextResolver() {
        return new DefaultExperienceDisclosureContextResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExperienceToolInvocationClassifier experienceToolInvocationClassifier(ExperienceExtensionProperties properties) {
        return new ExperienceToolInvocationClassifier(properties);
    }

    /**
     * 创建 ExperienceDisclosureService。
     * <p>不使用 @ConditionalOnBean 以避免与 @AutoConfigureAfter 的排序竞争问题。
     * 两个配置类共用同一个 property gate（experience.enabled），所以 ExperienceProvider /
     * ExperienceRepository 一定会被 ExperienceExtensionAutoConfiguration 创建。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExperienceDisclosureService experienceDisclosureService(ExperienceProvider experienceProvider,
                                                                   ExperienceRepository experienceRepository,
                                                                   ExperienceExtensionProperties properties,
                                                                   ExperienceToolInvocationClassifier classifier) {
        log.info("ExperienceDisclosureAutoConfiguration#experienceDisclosureService - reason=创建 ExperienceDisclosureService");
        return new ExperienceDisclosureService(experienceProvider, experienceRepository, properties, classifier);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExperiencePrefetchHook experiencePrefetchHook(ExperienceDisclosureService service,
                                                         ExperienceDisclosureContextResolver contextResolver,
                                                         ExperienceExtensionProperties properties) {
        log.info("ExperienceDisclosureAutoConfiguration#experiencePrefetchHook - reason=创建 ExperiencePrefetchHook");
        return new ExperiencePrefetchHook(service, contextResolver, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExperienceRuntimeModelInterceptor experienceRuntimeModelInterceptor(ExperienceDisclosureService service,
                                                                               ExperienceDisclosureContextResolver contextResolver,
                                                                               ExperienceToolInvocationClassifier classifier,
                                                                               ExperienceExtensionProperties properties) {
        return new ExperienceRuntimeModelInterceptor(service, contextResolver, classifier,
                properties.getReadExpDocMaxPaths());
    }

    /**
     * 将 search_exp / read_exp 工具暴露为独立 Bean，
     * 供 Agent Builder 注入（ModelInterceptor 的 getTools 不会被自动收集）。
     */
    @Bean(name = "experienceDisclosureTools")
    public List<ToolCallback> experienceDisclosureTools(ExperienceRuntimeModelInterceptor interceptor) {
        List<ToolCallback> tools = interceptor.getTools();
        log.info("ExperienceDisclosureAutoConfiguration#experienceDisclosureTools - reason=暴露披露工具, count={}", tools.size());
        return tools;
    }

    @Bean
    @ConditionalOnMissingBean
    public ExperienceRuntimeToolStateInterceptor experienceRuntimeToolStateInterceptor() {
        return new ExperienceRuntimeToolStateInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(ExperienceDisclosurePromptContributor.class)
    @ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
            name = "disclosure-prompt-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ExperienceDisclosurePromptContributor experienceDisclosurePromptContributor() {
        log.info("ExperienceDisclosureAutoConfiguration#experienceDisclosurePromptContributor - reason=创建 ExperienceDisclosurePromptContributor");
        return new ExperienceDisclosurePromptContributor();
    }
}
