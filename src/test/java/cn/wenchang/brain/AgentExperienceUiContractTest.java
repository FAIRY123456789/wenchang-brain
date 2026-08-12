package cn.wenchang.brain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExperienceUiContractTest {

    private static final Path STATIC_ROOT = Path.of("src", "main", "resources", "static");

    @Test
    void exposesComposerCommandBarAndUnifiedAgentSkillSelectors() throws IOException {
        String html = read("index.html");
        String app = read("app.js");
        String css = read("styles.css");

        assertThat(html)
                .contains("id=\"agentCommandBar\"", "data-command-state=\"COMMAND_IDLE\"")
                .contains("id=\"agentCommandTrigger\"", "id=\"skillCommandTrigger\"")
                .contains("id=\"commandPalette\"")
                .contains("id=\"composerSelections\"")
                .contains("选择智能体", "使用技能", "查看全部")
                .doesNotContain("id=\"sidebarAgentList\"", "class=\"agent-nav\"")
                .contains("文昌公共资源服务");
        assertThat(app)
                .contains("apiJson('/api/agents')", "apiJson('/api/skills')", "APP_BASE_PATH", "appUrl(url)")
                .contains("agentId: agent.id", "skillId: skill?.id || null")
                .contains("match(/(?:^|\\s)([@/])")
                .contains("handlePaletteKey(event)", "openCommandSelector('agent'", "openCommandSelector('skill'")
                .contains("message.agentRunJson", "message.toolsUsedJson || message.toolsJson")
                .contains("agentContextCard(agent)", "skillContextCard(skill)", "skill.outputType")
                .doesNotContain("function renderAgentNavigation()", "startNewChat(false, id)");
        assertThat(css)
                .contains(".agent-command-bar", ".command-idle", ".command-palette", ".palette-grid")
                .contains("grid-template-columns: repeat(3,minmax(0,1fr))");
    }

    @Test
    void agentRunSupportsNamedEventsAndCollapsesToPublicSummary() throws IOException {
        String app = read("app.js");
        String css = read("styles.css");

        assertThat(app)
                .contains("agent_selected", "skill_selected", "plan_created", "step_started")
                .contains("tool_started", "tool_completed", "source_found", "answer_chunk", "step_completed")
                .contains("已完成 ${completed} 个步骤 · ${toolsCount} 个工具 · ${run.sourceCount} 个来源")
                .contains("run.details.open = false");
        assertThat(css).contains(".agent-run", ".agent-run-step.running", ".agent-run-step.complete");
    }

    @Test
    void userVisibleCopyUsesFormalModelState() throws IOException {
        String html = read("index.html");
        String app = read("app.js");

        assertThat(html + app)
                .contains("模型未配置", "进入模型设置")
                .doesNotContain("本地演示模型", "本地回退模式", "Demo Model", "Local Model");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(STATIC_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
