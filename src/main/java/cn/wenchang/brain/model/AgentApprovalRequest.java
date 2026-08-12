package cn.wenchang.brain.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record AgentApprovalRequest(
        String agentRunId,
        @NotBlank String conversationId,
        String agentId,
        String skillId,
        @NotBlank String actionType,
        @NotBlank String operation,
        @NotBlank String impactScope,
        Map<String, Object> payload
) { }
