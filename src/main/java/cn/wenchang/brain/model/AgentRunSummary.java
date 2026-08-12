package cn.wenchang.brain.model;

import java.util.List;
import java.time.Instant;

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
        Instant completedAt
) {
    public AgentRunSummary {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public AgentRunSummary(String agentId, String agentName, String skillId, String skillName,
                           List<AgentRunStep> steps, int toolCount, int sourceCount, long latencyMs) {
        this(agentId, agentName, skillId, skillName, steps, toolCount, sourceCount, latencyMs,
                null, null, null, null);
    }
}
