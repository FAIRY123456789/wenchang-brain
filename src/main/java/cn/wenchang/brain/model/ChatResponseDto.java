package cn.wenchang.brain.model;

import cn.wenchang.brain.artifact.ArtifactMetadata;

import java.util.List;

public record ChatResponseDto(
        String answer,
        List<SourceRef> sources,
        List<String> toolsUsed,
        String traceId,
        long latencyMs,
        String modelMode,
        String modelProvider,
        String modelName,
        String conversationId,
        String agentId,
        String skillId,
        AgentRunSummary agentRun,
        List<ArtifactMetadata> artifacts
) {
    public ChatResponseDto {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public ChatResponseDto(String answer, List<SourceRef> sources, List<String> toolsUsed, String traceId,
                           long latencyMs, String modelMode, String modelProvider, String modelName,
                           String conversationId, String agentId, String skillId, AgentRunSummary agentRun) {
        this(answer, sources, toolsUsed, traceId, latencyMs, modelMode, modelProvider, modelName,
                conversationId, agentId, skillId, agentRun, List.of());
    }

    public ChatResponseDto(String answer, List<SourceRef> sources, List<String> toolsUsed, String traceId,
                           long latencyMs, String modelMode, String modelProvider, String modelName,
                           String conversationId) {
        this(answer, sources, toolsUsed, traceId, latencyMs, modelMode, modelProvider, modelName,
                conversationId, "wenchang", null, null, List.of());
    }

    public ChatResponseDto withConversationId(String id) {
        return new ChatResponseDto(answer, sources, toolsUsed, traceId, latencyMs, modelMode, modelProvider,
                modelName, id, agentId, skillId, agentRun, artifacts);
    }
}
