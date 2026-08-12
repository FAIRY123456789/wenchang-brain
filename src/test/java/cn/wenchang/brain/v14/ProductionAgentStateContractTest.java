package cn.wenchang.brain.v14;

import cn.wenchang.brain.model.AgentApprovalRequest;
import cn.wenchang.brain.model.AgentRunStep;
import cn.wenchang.brain.model.AgentRunSummary;
import cn.wenchang.brain.persistence.AgentApprovalRepository;
import cn.wenchang.brain.persistence.AgentRunRepository;
import cn.wenchang.brain.service.AgentApprovalService;
import cn.wenchang.brain.service.AgentRunPersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:production-agent-state;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.ai.default.api-key=",
        "wenchang.vector-store-file=target/production-agent-state/vector.json",
        "wenchang.trace-file=target/production-agent-state/trace.jsonl",
        "wenchang.web-search.enabled=false"
})
class ProductionAgentStateContractTest {

    @Autowired AgentRunPersistenceService runService;
    @Autowired AgentApprovalService approvalService;
    @Autowired AgentRunRepository runRepository;
    @Autowired AgentApprovalRepository approvalRepository;

    @AfterEach
    void clean() {
        approvalRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void persistsIndependentAgentRunAndStepDetails() {
        AgentRunSummary summary = new AgentRunSummary("policy", "政策助手", "policy-brief", "政策简报",
                List.of(new AgentRunStep("policy", "检索政策", "tool", "policySearch", "completed",
                        31, 3, "NATIVE", "取得3条政策", null, null, "topic=商业航天")),
                1, 3, 50);

        var saved = runService.persist("conversation-agent-state", "生成政策简报", summary);

        assertThat(saved.status()).isEqualTo("COMPLETED");
        assertThat(saved.steps()).singleElement().satisfies(step -> {
            assertThat(step.sequence()).isEqualTo(1);
            assertThat(step.toolName()).isEqualTo("policySearch");
            assertThat(step.toolSource()).isEqualTo("NATIVE");
            assertThat(step.summary()).contains("3条政策");
        });
        assertThat(runService.list("conversation-agent-state")).extracting("id").containsExactly(saved.id());
    }

    @Test
    void longTermMutationRequiresPreviewAndCanBeCancelledWithoutExecution() {
        var preview = approvalService.preview(new AgentApprovalRequest(null, "conversation-approval", "wenchang",
                null, "REINDEX_KNOWLEDGE", "重新索引文昌知识库", "全部 active 知识文档",
                Map.of("reason", "contract-test")));

        assertThat(preview.status()).isEqualTo("PENDING");
        var cancelled = approvalService.cancel(preview.id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.resultSummary()).contains("未修改");
    }

    @Test
    void acceptsEvalConversationIdsLongerThanUuid() {
        String conversationId = "agent-eval-" + java.util.UUID.randomUUID();
        AgentRunSummary summary = new AgentRunSummary("wenchang", "Wenchang", null, null,
                List.of(), 0, 0, 1);

        var saved = runService.persist(conversationId, "evaluation", summary);

        assertThat(saved.conversationId()).isEqualTo(conversationId);
        assertThat(runService.list(conversationId)).singleElement();
    }
}
