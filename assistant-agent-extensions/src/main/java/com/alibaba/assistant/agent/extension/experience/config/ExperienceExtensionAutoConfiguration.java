package com.alibaba.assistant.agent.extension.experience.config;

import com.alibaba.assistant.agent.extension.experience.hook.FastIntentReactHook;
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
 * <p>提供经验仓库、经验提供者、快速意图等基础 Bean。
 * 旧版的 ReactExperienceAgentHook / CommonSenseExperienceModelHook 已在 4.2
 * 重构中被渐进式披露系统（ExperienceDisclosureAutoConfiguration）取代并移除。
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
                          name = "fast-intent-react-enabled",
                          havingValue = "true",
                          matchIfMissing = true)
    public FastIntentReactHook fastIntentReactHook(ExperienceProvider experienceProvider,
                                                   ExperienceExtensionProperties properties,
                                                   FastIntentService fastIntentService) {
        log.info("ExperienceExtensionAutoConfiguration#fastIntentReactHook - reason=creating fast intent react hook bean");
        return new FastIntentReactHook(experienceProvider, properties, fastIntentService);
    }
}
