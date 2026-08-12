package cn.wenchang.brain.v14;

import cn.wenchang.brain.model.AgentRunEvent;
import cn.wenchang.brain.model.AgentRunStep;
import cn.wenchang.brain.model.AgentRunSummary;
import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.ConversationDetail;
import cn.wenchang.brain.model.SourceRef;
import cn.wenchang.brain.artifact.ArtifactDescriptor;
import cn.wenchang.brain.service.ConversationMemoryService;
import cn.wenchang.brain.service.ConversationService;
import cn.wenchang.brain.service.WenchangAgentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:v14-agent-run;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.ai.default.api-key=",
        "wenchang.vector-store-file=target/v14-test/vector.json",
        "wenchang.trace-file=target/v14-test/trace.jsonl",
        "wenchang.web-search.enabled=false"
})
class AgentRunPersistenceContractTest {

    @Autowired WenchangAgentService agentService;
    @Autowired ConversationService conversationService;
    @Autowired ConversationMemoryService memoryService;

    @AfterEach
    void cleanConversations() {
        conversationService.deleteAll();
    }

    @Test
    void streamPublishesPublicAgentRunInOrderWithoutInternalReasoning() {
        List<AgentRunEvent> events = new ArrayList<>();
        List<String> chunks = new ArrayList<>();

        ChatResponseDto response = agentService.stream("介绍文昌滨海生态和红树林", "v14-stream",
                "ecology", null, ignored -> { }, chunks::add, events::add);

        assertThat(response.agentId()).isEqualTo("ecology");
        assertThat(response.skillId()).isNull();
        assertThat(response.agentRun()).isNotNull();
        assertThat(response.agentRun().steps()).isNotEmpty()
                .allSatisfy(step -> assertThat(step.status()).isIn("completed", "failed"));
        assertThat(chunks).isNotEmpty();
        assertThat(events).extracting(AgentRunEvent::type)
                .startsWith("agent_selected", "plan_created")
                .contains("step_started", "source_found", "step_completed")
                .doesNotContain("chain_of_thought", "reasoning", "thought");
        int firstStarted = indexOf(events, "step_started");
        int firstCompleted = indexOf(events, "step_completed");
        assertThat(firstStarted).isLessThan(firstCompleted);
    }

    @Test
    void persistedConversationRestoresAgentSkillRunToolsAndSources() {
        var conversation = conversationService.resolveForChat(null, "核验文昌商业航天资料", "aerospace");
        conversationService.appendUser(conversation.id(), "核验文昌商业航天资料");

        AgentRunSummary run = new AgentRunSummary("aerospace", "航天研究员", "evidence-check", "证据核验",
                List.of(
                        new AgentRunStep("knowledge", "检索文昌知识库", "retrieval", "", "completed", 12, 1),
                        new AgentRunStep("tool-knowledgeEvidence", "核验知识证据", "tool",
                                "knowledgeEvidence", "completed", 8, 1),
                        new AgentRunStep("answer", "整理来源并生成结果", "answer", "", "completed", 10, 1)
                ), 1, 1, 30);
        ChatResponseDto response = new ChatResponseDto("已核验资料来源。",
                List.of(new SourceRef("knowledge/03_aerospace/source.md", "商业航天", "aerospace",
                        "海南省人民政府", "https://www.hainan.gov.cn/", "P0", "2025-01-01", "2026-08-11")),
                List.of("knowledgeEvidence"), "trace-v14-persist", 30, "REMOTE_DEFAULT", "deepseek",
                "deepseek-chat", conversation.id(), "aerospace", "evidence-check", run);
        conversationService.appendAssistant(conversation.id(), response);
        memoryService.clear(conversation.id());

        ConversationDetail restored = conversationService.detail(conversation.id());
        assertThat(restored.agentId()).isEqualTo("aerospace");
        assertThat(restored.messages()).hasSize(2);
        assertThat(restored.messages().get(1).agentId()).isEqualTo("aerospace");
        assertThat(restored.messages().get(1).skillId()).isEqualTo("evidence-check");
        assertThat(restored.messages().get(1).agentRunJson()).contains("aerospace", "evidence-check", "steps");
        assertThat(restored.messages().get(1).toolsUsedJson()).contains("knowledgeEvidence");
        assertThat(restored.messages().get(1).sourcesJson()).isNotBlank().isNotEqualTo("[]");
        assertThat(memoryService.restoredMessageCount(conversation.id())).isEqualTo(2);
    }

    @Test
    void persistedConversationRestoresArtifactDescriptorAndAgentRunOutput() {
        var conversation = conversationService.resolveForChat(null, "生成高中名单 Word", "wenchang");
        conversationService.appendUser(conversation.id(), "生成高中名单 Word");
        ArtifactDescriptor artifact = new ArtifactDescriptor("artifact-restore", conversation.id(), "WORD",
                "文昌市高中名单报告.docx", "文昌市高中名单报告",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 2048,
                "2026-08-12T00:00:00Z", "/api/artifacts/artifact-restore/download", false, 6,
                "wenchang", "word-report");
        AgentRunStep fileStep = new AgentRunStep("word", "生成 Word 报告", "tool",
                "createWenchangWordReport", "completed", 40, 6, "MCP", "文件已生成",
                null, null, "{}", List.of(artifact.id()));
        AgentRunSummary run = new AgentRunSummary("wenchang", "Wenchang Assistant", "word-report", "生成 Word",
                List.of(fileStep), 1, 6, 50, "run-artifact", "COMPLETED", null, null, List.of(artifact));
        ChatResponseDto response = new ChatResponseDto("报告已生成，见下方文件。", List.of(),
                List.of("createWenchangWordReport"), "trace-artifact", 50, "REMOTE_DEFAULT", "deepseek",
                "deepseek-chat", conversation.id(), "wenchang", "word-report", run, List.of(artifact));
        conversationService.appendAssistant(conversation.id(), response);

        ConversationDetail restored = conversationService.detail(conversation.id());
        assertThat(restored.messages()).hasSize(2);
        assertThat(restored.messages().get(1).artifactsJson()).contains(
                "artifact-restore", "文昌市高中名单报告.docx", "downloadUrl");
        assertThat(restored.messages().get(1).agentRunJson()).contains("artifact-restore", "artifactIds");
    }

    private static int indexOf(List<AgentRunEvent> events, String type) {
        for (int index = 0; index < events.size(); index++) {
            if (type.equals(events.get(index).type())) return index;
        }
        return -1;
    }
}
