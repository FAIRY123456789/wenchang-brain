package cn.wenchang.brain;

import cn.wenchang.brain.agent.AgentProfileRegistry;
import cn.wenchang.brain.controller.AgentExperienceController;
import cn.wenchang.brain.skill.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentCommandExperienceV15ContractTest {

    private static final Path STATIC_ROOT = Path.of("src", "main", "resources", "static");

    @Test
    void detailApisExposeEnglishAgentIdentityAndChineseTaskMetadata() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentExperienceController(
                new AgentProfileRegistry(), new SkillRegistry())).build();

        mvc.perform(get("/api/agents/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayNameEn").value("Policy Assistant"))
                .andExpect(jsonPath("$.descriptionZh").isNotEmpty())
                .andExpect(jsonPath("$.capabilitiesZh").isArray())
                .andExpect(jsonPath("$.acceptedInputsZh").isArray())
                .andExpect(jsonPath("$.skills").isArray())
                .andExpect(jsonPath("$.tools").isArray())
                .andExpect(jsonPath("$.workflowZh").isArray())
                .andExpect(jsonPath("$.humanApprovalZh").isNotEmpty())
                .andExpect(jsonPath("$.examplesZh").isArray());

        mvc.perform(get("/api/skills/policy-brief"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command").value("/政策简报"))
                .andExpect(jsonPath("$.displayNameZh").isNotEmpty())
                .andExpect(jsonPath("$.descriptionZh").isNotEmpty())
                .andExpect(jsonPath("$.inputHintZh").isNotEmpty())
                .andExpect(jsonPath("$.outputZh").isNotEmpty())
                .andExpect(jsonPath("$.artifactTypes").isArray());
    }

    @Test
    void commandBarHasStableStatesKeyboardTriggersAndAgentSkillComposition() throws IOException {
        String html = read("index.html");
        String app = read("app.js");

        assertThat(html).contains("agent-command-bar", "agentCommandTrigger", "skillCommandTrigger");
        assertThat(app).contains(
                "COMMAND_IDLE", "AGENT_SELECTING", "SKILL_SELECTING", "AGENT_SELECTED",
                "SKILL_SELECTED", "AGENT_AND_SKILL_SELECTED", "DETAIL_OPEN",
                "event.key === 'Tab'", "event.key === 'Enter'", "event.key === 'Escape'",
                "match(/(?:^|\\s)([@/])", "state.selectedAgentId", "state.selectedSkillId");
        assertThat(app).contains("agentId: agent.id", "skillId: skill?.id || null");
    }

    @Test
    void agentChoiceDoesNotCreateConversationAndConversationExperienceRestores() throws IOException {
        String app = read("app.js");

        assertThat(app)
                .contains("function restoreConversationExperience(detail)")
                .contains("detail.agentId || experienceMessage?.agentId || 'wenchang'")
                .contains("detail.skillId || experienceMessage?.skillId || null")
                .contains("function startWithDetailedAgent()", "selectAgent(id)")
                .doesNotContain("startNewChat(false, id)");
    }

    @Test
    void agentSurfacesAreTextFirstReadableAndResponsive() throws IOException {
        String html = read("index.html");
        String app = read("app.js");
        String css = read("styles.css");
        String i18n = read("i18n.js");

        assertThat(html).doesNotContain("agentDetailGlyph", "sidebarAgentList");
        assertThat(app).doesNotContain("function agentGlyph(", "item.icon");
        assertThat(css)
                .contains(".agent-context-card strong", "font-size: 17px")
                .contains("backdrop-filter: blur(22px) saturate(145%)")
                .contains("background: linear-gradient(135deg,rgba(255,255,255,.62),rgba(230,245,251,.48))")
                .contains(".agent-detail-panel h2", "font-size: 28px")
                .contains("width: min(820px,calc(100vw - 48px))")
                .contains(".skill-detail-panel", "width: min(680px,calc(100vw - 48px))")
                .contains("@media (max-width: 600px)", "grid-template-columns: repeat(2,minmax(0,1fr))")
                .contains(".agent-detail-panel h2 { font-size: 24px; }");

        assertThat(html).contains("skillDetailDialog", "skillDetailTitle", "skillDetailUse");
        assertThat(app).contains("function openSkillDetail(id, returnFocus", "palette-option-detail");
        assertThat(i18n).contains("'palette.viewDetails': '查看说明'");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(STATIC_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
