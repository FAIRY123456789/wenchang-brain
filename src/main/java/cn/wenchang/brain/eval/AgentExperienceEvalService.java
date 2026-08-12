package cn.wenchang.brain.eval;

import cn.wenchang.brain.artifact.ArtifactService;
import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.service.WenchangAgentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AgentExperienceEvalService {

    private final WenchangAgentService agentService;
    private final ArtifactService artifactService;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public AgentExperienceEvalService(WenchangAgentService agentService, ArtifactService artifactService) {
        this.agentService = agentService;
        this.artifactService = artifactService;
    }

    public AgentExperienceEvalReport run() throws IOException {
        List<AgentExperienceEvalCase> cases = mapper.readValue(
                new ClassPathResource("eval/agent_experience_eval.json").getInputStream(), new TypeReference<>() { });
        List<AgentExperienceEvalResult> results = new ArrayList<>();
        for (AgentExperienceEvalCase item : cases) {
            try {
                ChatResponseDto response = agentService.chat(item.question(), "agent-eval-" + UUID.randomUUID(),
                        item.agentId(), item.skillId());
                List<String> failures = evaluate(item, response);
                results.add(new AgentExperienceEvalResult(item.id(), item.agentId(), item.skillId(),
                        failures.isEmpty() ? "PASS" : "FAIL", String.join("；", failures), response.toolsUsed(),
                        response.sources().size(), response.agentRun() == null ? 0 : response.agentRun().steps().size(),
                        response.artifacts().size(), response.agentRun() == null ? "" : response.agentRun().status(),
                        response.latencyMs()));
            } catch (Exception exception) {
                results.add(new AgentExperienceEvalResult(item.id(), item.agentId(), item.skillId(), "FAIL",
                        exception.getClass().getSimpleName() + ": " + exception.getMessage(), List.of(), 0, 0,
                        0, "FAILED", 0));
            }
        }
        int passed = (int) results.stream().filter(item -> "PASS".equals(item.status())).count();
        return new AgentExperienceEvalReport(Instant.now(), passed, results.size() - passed, List.copyOf(results));
    }

    private List<String> evaluate(AgentExperienceEvalCase item, ChatResponseDto response) {
        List<String> failures = new ArrayList<>();
        if (!item.agentId().equals(response.agentId())) failures.add("Agent 不匹配");
        if (item.skillId() != null && !item.skillId().equals(response.skillId())) failures.add("Skill 不匹配");
        if (!response.toolsUsed().containsAll(item.expectedTools())) failures.add("缺少工具 " + item.expectedTools());
        if (!item.expectedAnyTools().isEmpty() && item.expectedAnyTools().stream().noneMatch(response.toolsUsed()::contains)) {
            failures.add("未命中任一预期工具 " + item.expectedAnyTools());
        }
        if (response.sources().size() < item.minSources()) failures.add("来源不足");
        int steps = response.agentRun() == null ? 0 : response.agentRun().steps().size();
        if (steps < item.minSteps()) failures.add("公开步骤不足");
        if (!item.expectedCategories().isEmpty() && response.sources().stream()
                .map(source -> source.category() == null ? "" : source.category())
                .noneMatch(item.expectedCategories()::contains)) failures.add("分类不匹配");
        if (response.artifacts().size() < item.minArtifacts()) failures.add("Artifact 数量不足");
        if (!item.expectedArtifactTypes().isEmpty() && response.artifacts().stream()
                .map(artifact -> artifact.type() == null ? "" : artifact.type())
                .noneMatch(item.expectedArtifactTypes()::contains)) failures.add("Artifact 类型不匹配");
        boolean expectsSourcedDocument = item.expectedArtifactTypes().stream().anyMatch(type -> type.contains("WORD"));
        if (expectsSourcedDocument && response.artifacts().stream()
                .filter(artifact -> item.expectedArtifactTypes().contains(artifact.type()))
                .noneMatch(artifact -> artifact.sourceCount() > 0)) failures.add("Word Artifact 缺少来源");
        for (var artifact : response.artifacts()) {
            try {
                var file = artifactService.open(artifact.id());
                if (java.nio.file.Files.size(file.path()) <= 0) failures.add("Artifact 文件为空");
            } catch (Exception exception) {
                failures.add("Artifact 无法打开：" + artifact.id());
            }
        }
        if (response.agentRun() == null || response.agentRun().status() == null
                || !response.agentRun().status().startsWith("COMPLETED")) failures.add("Agent Run 未完成");
        return failures;
    }
}
