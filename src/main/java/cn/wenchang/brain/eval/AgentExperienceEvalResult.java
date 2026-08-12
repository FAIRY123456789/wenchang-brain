package cn.wenchang.brain.eval;

import java.util.List;

public record AgentExperienceEvalResult(
        String id, String agentId, String skillId, String status, String reason,
        List<String> toolsUsed, int sources, int steps, int artifacts, String runStatus, long latencyMs
) { }
