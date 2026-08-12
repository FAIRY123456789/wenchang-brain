package cn.wenchang.brain.model;

import java.time.Instant;
import java.util.List;

public record PersistedAgentRun(
        String id, String conversationId, String agentId, String skillId, String goal,
        String status, Instant startedAt, Instant completedAt, List<PersistedAgentStep> steps,
        String artifactsJson
) { }
