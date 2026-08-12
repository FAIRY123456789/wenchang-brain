package cn.wenchang.brain.service;

import cn.wenchang.brain.model.AgentRunSummary;
import cn.wenchang.brain.model.PersistedAgentRun;
import cn.wenchang.brain.model.PersistedAgentStep;
import cn.wenchang.brain.persistence.AgentRunEntity;
import cn.wenchang.brain.persistence.AgentRunRepository;
import cn.wenchang.brain.persistence.AgentStepEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AgentRunPersistenceService {

    private final AgentRunRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AgentRunPersistenceService(AgentRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PersistedAgentRun persist(String conversationId, String goal, AgentRunSummary summary) {
        Instant completedAt = Instant.now();
        Instant startedAt = completedAt.minusMillis(Math.max(0, summary.latencyMs()));
        String runId = summary.id() == null || summary.id().isBlank() ? UUID.randomUUID().toString() : summary.id();
        String runStatus = summary.status() == null || summary.status().isBlank()
                ? deriveStatus(summary) : summary.status();
        AgentRunEntity run = new AgentRunEntity(runId, conversationId, summary.agentId(), summary.skillId(),
                goal, runStatus, summary.startedAt() == null ? startedAt : summary.startedAt());
        run.attachArtifacts(json(summary.artifacts()));
        int sequence = 0;
        for (var step : summary.steps()) {
            Instant stepStarted = run.getStartedAt();
            Instant stepCompleted = "RUNNING".equalsIgnoreCase(step.status()) ? null : completedAt;
            AgentStepEntity entity = new AgentStepEntity(run, ++sequence, step.label(), step.type(), step.toolName(),
                    step.toolSource(), normalizeStatus(step.status()), stepStarted, stepCompleted,
                    step.latencyMs(), step.summary(), step.errorType(), step.errorMessage(), step.inputPreview());
            entity.attachArtifactIds(json(step.artifactIds()));
            run.addStep(entity);
        }
        if (!"WAITING_APPROVAL".equals(runStatus)) {
            run.complete(runStatus, summary.completedAt() == null ? completedAt : summary.completedAt());
        }
        return toDto(repository.save(run));
    }

    @Transactional(readOnly = true)
    public List<PersistedAgentRun> list(String conversationId) {
        return repository.findAllByConversationIdOrderByStartedAtDesc(conversationId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PersistedAgentRun require(String id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Agent Run 不存在"));
    }

    private String deriveStatus(AgentRunSummary summary) {
        return summary.steps().stream().anyMatch(step -> "failed".equalsIgnoreCase(step.status()))
                ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
    }

    private String normalizeStatus(String value) {
        return value == null || value.isBlank() ? "WAITING" : value.toUpperCase();
    }

    private PersistedAgentRun toDto(AgentRunEntity entity) {
        return new PersistedAgentRun(entity.getId(), entity.getConversationId(), entity.getAgentId(),
                entity.getSkillId(), entity.getGoal(), entity.getStatus(), entity.getStartedAt(),
                entity.getCompletedAt(), entity.getSteps().stream().map(step -> new PersistedAgentStep(
                        step.getId(), step.getSequence(), step.getName(), step.getStage(), step.getToolName(),
                        step.getToolSource(), step.getStatus(), step.getStartedAt(), step.getCompletedAt(),
                        step.getLatencyMs(), step.getSummary(), step.getErrorType(), step.getErrorMessage(),
                        step.getInputPreview(), step.getArtifactIdsJson())).toList(), entity.getArtifactsJson());
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
        catch (Exception ignored) { return "[]"; }
    }
}
