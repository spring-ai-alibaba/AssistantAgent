package com.alibaba.assistant.agent.extension.experience.disclosure;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.DirectExperienceGrounding;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ExperienceCandidateCard;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.GroupedExperienceCandidates;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ToolInvocationPath;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.prompt.PromptContribution;
import com.alibaba.assistant.agent.prompt.PromptContributorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceDisclosurePromptContributorTest {

    private ExperienceDisclosurePromptContributor contributor;

    @BeforeEach
    void setUp() {
        contributor = new ExperienceDisclosurePromptContributor();
    }

    @Test
    void shouldNotContributeWhenNoCandidatesOrGroundings() {
        PromptContributorContext ctx = contextWithAttributes(Map.of());
        assertFalse(contributor.shouldContribute(ctx));
    }

    @Test
    void shouldContributeWhenCandidatesExist() {
        GroupedExperienceCandidates candidates = new GroupedExperienceCandidates();
        ExperienceCandidateCard card = new ExperienceCandidateCard();
        card.setId("exp-1");
        card.setExperienceType(ExperienceType.REACT);
        card.setTitle("Test Workflow");
        card.setDescription("A test workflow experience");
        candidates.setReactCandidates(List.of(card));

        PromptContributorContext ctx = contextWithAttributes(Map.of(
                CodeactStateKeys.EXPERIENCE_PREFETCHED_CANDIDATES, candidates
        ));

        assertTrue(contributor.shouldContribute(ctx));
    }

    @Test
    void shouldContributeWhenDirectGroundingsExist() {
        DirectExperienceGrounding grounding = new DirectExperienceGrounding();
        grounding.setId("exp-2");
        grounding.setExperienceType(ExperienceType.COMMON);
        grounding.setTitle("Concept Explanation");
        grounding.setContent("Short direct content");
        grounding.setDisclosureStrategy("DIRECT");

        PromptContributorContext ctx = contextWithAttributes(Map.of(
                CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS, List.of(grounding)
        ));

        assertTrue(contributor.shouldContribute(ctx));
    }

    @Test
    void contributeBuildsPromptWithCandidatesAndGroundings() {
        // Set up direct grounding
        DirectExperienceGrounding grounding = new DirectExperienceGrounding();
        grounding.setId("exp-direct");
        grounding.setExperienceType(ExperienceType.COMMON);
        grounding.setTitle("What is CR");
        grounding.setContent("CR stands for Change Request");
        grounding.setDisclosureStrategy("DIRECT");
        grounding.setScore(0.95);

        // Set up candidates
        GroupedExperienceCandidates candidates = new GroupedExperienceCandidates();

        ExperienceCandidateCard reactCard = new ExperienceCandidateCard();
        reactCard.setId("exp-react");
        reactCard.setExperienceType(ExperienceType.REACT);
        reactCard.setTitle("Deploy Workflow");
        reactCard.setDescription("Standard deployment workflow");
        candidates.setReactCandidates(List.of(reactCard));

        ExperienceCandidateCard toolCard = new ExperienceCandidateCard();
        toolCard.setId("exp-tool");
        toolCard.setExperienceType(ExperienceType.TOOL);
        toolCard.setTitle("Create CR Tool");
        toolCard.setToolInvocationPath(ToolInvocationPath.REACT_DIRECT);
        toolCard.setCallableToolName("aone::create_cr");
        candidates.setToolCandidates(List.of(toolCard));

        PromptContributorContext ctx = contextWithAttributes(Map.of(
                CodeactStateKeys.EXPERIENCE_PREFETCHED_CANDIDATES, candidates,
                CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS, List.of(grounding)
        ));

        PromptContribution contribution = contributor.contribute(ctx);
        assertNotNull(contribution);
        assertFalse(contribution.isEmpty());
        assertEquals(1, contribution.messagesToAppend().size());

        Message message = contribution.messagesToAppend().get(0);
        assertInstanceOf(UserMessage.class, message);
        String text = ((UserMessage) message).getText();

        // Verify structural elements (XML tags)
        assertTrue(text.contains("<experience_disclosure>"));
        assertTrue(text.contains("</experience_disclosure>"));
        assertTrue(text.contains("DIRECT grounding already available:"));
        assertTrue(text.contains("id=exp-direct"));
        assertTrue(text.contains("CR stands for Change Request"));
        assertTrue(text.contains("REACT workflow candidates"));
        assertTrue(text.contains("id=exp-react"));
        assertTrue(text.contains("TOOL capability candidates"));
        assertTrue(text.contains("id=exp-tool"));
        assertTrue(text.contains("invocationPath=REACT_DIRECT"));
        assertTrue(text.contains("toolName=aone::create_cr"));

        // Verify guidance text (Chinese body with tool references)
        assertTrue(text.contains("search_exp"));
        assertTrue(text.contains("read_exp"));
        assertTrue(text.contains("read_exp_doc"), "prompt must describe read_exp_doc");
        assertTrue(text.contains("referenceManifest") || text.contains("manifest"),
                "prompt must mention manifest");
        assertTrue(text.contains("/workspace/") || text.contains("沙箱"),
                "prompt must describe sandbox workspace for assets");
        assertTrue(text.contains("PROGRESSIVE"));
        assertTrue(text.contains("DIRECT"));
    }

    @Test
    void contributeFiltersAlreadyDisclosedCandidates() {
        // Direct grounding for exp-1
        DirectExperienceGrounding grounding = new DirectExperienceGrounding();
        grounding.setId("exp-1");
        grounding.setExperienceType(ExperienceType.COMMON);
        grounding.setTitle("Already Disclosed");
        grounding.setContent("Content");

        // Candidates include exp-1 (should be filtered) and exp-2 (should remain)
        GroupedExperienceCandidates candidates = new GroupedExperienceCandidates();
        ExperienceCandidateCard card1 = new ExperienceCandidateCard();
        card1.setId("exp-1");
        card1.setExperienceType(ExperienceType.COMMON);
        card1.setTitle("Already Disclosed");

        ExperienceCandidateCard card2 = new ExperienceCandidateCard();
        card2.setId("exp-2");
        card2.setExperienceType(ExperienceType.COMMON);
        card2.setTitle("Not Yet Disclosed");

        candidates.setCommonCandidates(List.of(card1, card2));

        PromptContributorContext ctx = contextWithAttributes(Map.of(
                CodeactStateKeys.EXPERIENCE_PREFETCHED_CANDIDATES, candidates,
                CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS, List.of(grounding)
        ));

        PromptContribution contribution = contributor.contribute(ctx);
        String text = ((UserMessage) contribution.messagesToAppend().get(0)).getText();

        // exp-1 should appear in DIRECT section but NOT in candidates section
        assertTrue(text.contains("DIRECT grounding already available:"));
        assertTrue(text.contains("id=exp-1"));
        // exp-2 should appear in candidates
        assertTrue(text.contains("id=exp-2"));
        assertTrue(text.contains("Not Yet Disclosed"));
    }

    @Test
    void nameAndPriorityAreCorrect() {
        assertEquals("ExperienceDisclosurePromptContributor", contributor.getName());
        assertEquals(18, contributor.getPriority());

        ExperienceDisclosurePromptContributor custom = new ExperienceDisclosurePromptContributor(5);
        assertEquals(5, custom.getPriority());
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private PromptContributorContext contextWithAttributes(Map<String, Object> attrs) {
        Map<String, Object> mutableAttrs = new HashMap<>(attrs);
        return new PromptContributorContext() {
            @Override
            public List<Message> getMessages() {
                return List.of();
            }

            @Override
            public Optional<SystemMessage> getSystemMessage() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> getAttributes() {
                return mutableAttrs;
            }

            @Override
            public Optional<String> getPhase() {
                return Optional.of("REACT");
            }
        };
    }
}
