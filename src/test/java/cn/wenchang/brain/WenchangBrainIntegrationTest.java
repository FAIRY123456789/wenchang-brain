package cn.wenchang.brain;

import cn.wenchang.brain.eval.EvalReport;
import cn.wenchang.brain.eval.EvalService;
import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.KnowledgeStatus;
import cn.wenchang.brain.rag.KnowledgeService;
import cn.wenchang.brain.service.WenchangAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:v10;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.vector-store-file=target/test-data/wenchang-vector-store.json",
        "wenchang.trace-file=target/test-logs/agent-trace.jsonl",
        "wenchang.web-search.enabled=false"
})
class WenchangBrainIntegrationTest {

    @Autowired KnowledgeService knowledgeService;
    @Autowired WenchangAgentService agentService;
    @Autowired EvalService evalService;

    @Test
    void ingestsAllMarkdownFilesAndPersistsChunks() {
        KnowledgeStatus status = knowledgeService.getStatus();
        assertThat(status.files()).isGreaterThanOrEqualTo(25);
        assertThat(status.documents()).isGreaterThanOrEqualTo(25);
        assertThat(status.chunks()).isGreaterThan(50);
        assertThat(status.categories()).hasSizeGreaterThanOrEqualTo(15);
        assertThat(status.sourceLevels()).containsKey("P0");
        assertThat(status.sources()).isGreaterThanOrEqualTo(25);
        assertThat(status.persisted()).isTrue();
        assertThat(status.chunksPerFile()).hasSize(status.files());
    }

    @Test
    void ragReturnsAnswerAndConcreteSources() {
        ChatResponseDto response = agentService.chat("文昌有哪些航天与生态特色？", "test-rag-session");
        assertThat(response.answer()).isNotBlank();
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.sources().get(0).file()).endsWith(".md");
        assertThat(response.sources().get(0).file()).contains("/");
        assertThat(response.traceId()).isNotBlank();
    }

    @Test
    void temporalQuestionDeterministicallyInvokesWebSearchTool() {
        ChatResponseDto response = agentService.chat("文昌近期一次航天发射是什么？", "test-tool-session");
        assertThat(response.toolsUsed()).contains("webSearch");
    }

    @Test
    void v13HarnessCoversTwentyFiveKnowledgeAndToolCases() throws Exception {
        EvalReport report = evalService.run();
        assertThat(report.results()).hasSize(25);
        assertThat(report.failed()).isZero();
        assertThat(report.passed()).isEqualTo(25);
    }
}
