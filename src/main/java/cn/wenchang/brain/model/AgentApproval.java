package cn.wenchang.brain.model;

import java.time.Instant;

public record AgentApproval(
        String id, String agentRunId, String conversationId, String agentId, String skillId,
        String actionType, String operation, String impactScope, String status,
        Instant createdAt, Instant decidedAt, String resultSummary
) { }
