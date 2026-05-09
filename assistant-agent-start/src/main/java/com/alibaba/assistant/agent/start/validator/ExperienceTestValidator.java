package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Experience Module Test Validator
 *
 * <p>Tests experience query functionality after application startup to ensure
 * experience hooks are working correctly.
 * 
 * <p>验证 COMMON 和 REACT 类型的经验。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@Order(1000) // Run after experience initialization
public class ExperienceTestValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ExperienceTestValidator.class);

    private final ExperienceRepository experienceRepository;
    private final ExperienceProvider experienceProvider;

    public ExperienceTestValidator(ExperienceRepository experienceRepository,
                                 ExperienceProvider experienceProvider) {
        this.experienceRepository = experienceRepository;
        this.experienceProvider = experienceProvider;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("ExperienceTestValidator#run - reason=Starting experience module validation");

        testExperienceCount();
        testCommonExperienceQuery();
        testReactExperienceQuery();

        log.info("ExperienceTestValidator#run - reason=Experience module validation completed");
    }

    private void testExperienceCount() {
        long totalCount = experienceRepository.count();
        long reactCount = experienceRepository.countByType(ExperienceType.REACT);
        long commonCount = experienceRepository.countByType(ExperienceType.COMMON);

        log.info("ExperienceTestValidator#testExperienceCount - reason=Experience stats: total={}, react={}, common={}",
                totalCount, reactCount, commonCount);
    }

    private void testCommonExperienceQuery() {
        log.info("ExperienceTestValidator#testCommonExperienceQuery - reason=Testing common experience query");

        ExperienceQuery query = new ExperienceQuery(ExperienceType.COMMON);
        ExperienceQueryContext context = new ExperienceQueryContext();
        context.setTenantId("test-user");

        List<Experience> experiences = experienceProvider.query(query, context);

        log.info("ExperienceTestValidator#testCommonExperienceQuery - reason=Found {} common experiences", experiences.size());
        for (Experience exp : experiences) {
            log.debug("ExperienceTestValidator#testCommonExperienceQuery - reason=Common experience: title={}, tags={}",
                     exp.getTitle(), exp.getTags());
        }

        // Verify MoLiHong identity experience exists
        boolean hasMoLiHongIdentity = experiences.stream()
                .anyMatch(exp -> exp.getTitle().contains("魔力红身份介绍"));

        if (hasMoLiHongIdentity) {
            log.info("ExperienceTestValidator#testCommonExperienceQuery - reason=✅ Found MoLiHong identity experience");
        } else {
            log.warn("ExperienceTestValidator#testCommonExperienceQuery - reason=❌ MoLiHong identity experience not found");
        }
    }

    private void testReactExperienceQuery() {
        log.info("ExperienceTestValidator#testReactExperienceQuery - reason=Testing react experience query");

        ExperienceQuery query = new ExperienceQuery(ExperienceType.REACT);
        ExperienceQueryContext context = new ExperienceQueryContext();

        List<Experience> experiences = experienceProvider.query(query, context);

        log.info("ExperienceTestValidator#testReactExperienceQuery - reason=Found {} react experiences", experiences.size());

        boolean hasWhoAreYou = experiences.stream()
                .anyMatch(exp -> exp.getTitle().contains("身份询问响应策略"));

        if (hasWhoAreYou) {
            log.info("ExperienceTestValidator#testReactExperienceQuery - reason=✅ Found who-are-you react experience");
        } else {
            log.warn("ExperienceTestValidator#testReactExperienceQuery - reason=❌ Who-are-you react experience not found");
        }
    }
}
