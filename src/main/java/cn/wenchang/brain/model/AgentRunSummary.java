package cn.wenchang.brain.model;

import java.util.List;
import java.time.Instant;
import cn.wenchang.brain.artifact.ArtifactDescriptor;

public record AgentRunSummary(
        String agentId,
        String agentName,
        String skillId,
        String skillName,
        List<AgentRunStep> steps,
        int toolCount,
        int sourceCount,
        long latencyMs,
        String id,
        String status,
        Instant startedAt,
        Instant completedAt,
        List<ArtifactDescriptor> artifacts
) {
    public AgentRunSummary {
        steps = steps == null ? List.of() : List.copyOf(steps);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public AgentRunSummary(String agentId, String agentName, String skillId, String skillName,
                           List<AgentRunStep> steps, int toolCount, int sourceCount, long latencyMs,
                           String id, String status, Instant startedAt, Instant completedAt) {
        this(agentId, agentName, skillId, skillName, steps, toolCount, sourceCount, latencyMs,
                id, status, startedAt, completedAt, List.of());
    }

    public AgentRunSummary(String agentId, String agentName, String skillId, String skillName,
                           List<AgentRunStep> steps, int toolCount, int sourceCount, long latencyMs) {
        this(agentId, agentName, skillId, skillName, steps, toolCount, sourceCount, latencyMs,
                null, null, null, null, List.of());
    }

    public int artifactCount() { return artifacts.size(); }

    @com.fasterxml.jackson.annotation.JsonProperty("artifactCount")
    public int artifactCountJson() { return artifacts.size(); }
}
