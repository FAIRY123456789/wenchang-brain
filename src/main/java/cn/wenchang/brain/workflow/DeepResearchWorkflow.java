package cn.wenchang.brain.workflow;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 为深度研究生成 4 至 6 个可公开执行步骤。 */
@Service
public class DeepResearchWorkflow {

    public AgentRunPlan plan(ResearchRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("research question must not be blank");
        }
        List<AgentRunStep> steps = new ArrayList<>();
        steps.add(step("understand", "明确研究范围与需要核验的事实", "planning"));
        steps.add(step("knowledge", "检索文昌知识库", "retrieval", "knowledgeEvidence"));
        if (request.requireOfficialSources()) {
            steps.add(step("official", "查询相关官方资料", "verification", "officialSourceSearch"));
        }
        if (request.requireCurrentInformation()) {
            steps.add(step("current", "检索近期公开信息", "research", "webSearch"));
        }
        steps.add(step("evidence", "整理证据与来源一致性", "synthesis"));
        steps.add(step("answer", "生成结构化研究结果", "answer"));
        return new AgentRunPlan("deep-research", steps);
    }

    private AgentRunStep step(String id, String title, String stage, String... tools) {
        return new AgentRunStep(id, title, stage, List.of(tools));
    }

    public record ResearchRequest(String question, boolean requireOfficialSources,
                                  boolean requireCurrentInformation) { }
}
