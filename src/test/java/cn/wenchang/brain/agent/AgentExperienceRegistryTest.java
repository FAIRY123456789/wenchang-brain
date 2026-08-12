package cn.wenchang.brain.agent;

import cn.wenchang.brain.skill.SkillRegistry;
import cn.wenchang.brain.skill.WorkflowType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExperienceRegistryTest {

    @Test
    void exposesFiveCompleteProfilesAndDefaultsToWenchang() {
        AgentProfileRegistry registry = new AgentProfileRegistry();

        assertThat(registry.all()).hasSize(5)
                .extracting(AgentProfile::id)
                .containsExactly("wenchang", "aerospace", "ecology", "study-tour", "policy");
        assertThat(registry.all()).extracting(AgentProfile::displayName)
                .containsExactly("Wenchang Assistant", "Aerospace Researcher", "Ecology Researcher",
                        "Study Tour Planner", "Policy Assistant");
        assertThat(registry.resolve(null).id()).isEqualTo("wenchang");
        assertThat(registry.resolve("missing").id()).isEqualTo("wenchang");
        assertThat(registry.require("aerospace").preferredTools())
                .contains("officialSourceSearch", "webSearch", "policySearch");
        assertThat(registry.all()).allSatisfy(profile -> {
            assertThat(profile.systemInstruction()).isNotBlank();
            assertThat(profile.knowledgeCategories()).isNotEmpty();
            assertThat(profile.suggestedSkills()).isNotEmpty();
            assertThat(profile.responseStyle()).isNotBlank();
            assertThat(profile.taskCapabilities()).isNotEmpty();
            assertThat(profile.acceptedInputs()).isNotEmpty();
            assertThat(profile.typicalWorkflow()).isNotEmpty();
            assertThat(profile.exampleTasks()).isNotEmpty();
            assertThat(profile.completionCriteria()).isNotBlank();
            assertThat(profile.contextSummary()).isNotBlank();
        });
    }

    @Test
    void exposesProductionSkillsWithTaskOutputMetadata() {
        SkillRegistry registry = new SkillRegistry();

        assertThat(registry.all()).hasSize(13)
                .extracting(skill -> skill.id())
                .containsExactly("web-search", "official-search", "evidence-check", "place-search",
                        "study-tour-plan", "policy-search", "deep-research", "public-service",
                        "latest-policy", "policy-compare", "word-report", "data-export", "policy-brief");
        assertThat(registry.require("public-service").requiredTools()).containsExactly("searchPublicServices");
        assertThat(registry.require("deep-research").workflowType()).isEqualTo(WorkflowType.DEEP_RESEARCH);
        assertThat(registry.require("deep-research").requiredTools())
                .contains("collectOfficialMaterials", "createWenchangWordReport");
        assertThat(registry.require("policy-brief").requiredTools())
                .containsExactly("policySearch", "officialSourceSearch", "webSearch", "knowledgeEvidence",
                        "createPolicyBrief");
        assertThat(registry.all()).allSatisfy(skill -> {
            assertThat(skill.command()).startsWith("/");
            assertThat(skill.requiredTools()).isNotEmpty();
            assertThat(skill.systemInstruction()).isNotBlank();
            assertThat(skill.group()).isIn("研究", "工作成果", "数据与地点");
            assertThat(skill.outputType()).isNotBlank();
            assertThat(skill.approvalPolicy()).isNotBlank();
        });
        assertThat(registry.require("word-report").artifactType()).isEqualTo("WORD");
        assertThat(registry.require("deep-research").artifactType()).isEqualTo("WORD");
        assertThat(registry.require("data-export").outputType()).contains("CSV", "Excel");
        assertThat(registry.require("data-export").requiredTools())
                .containsExactly("placeSearch", "exportWenchangData");
    }
}
