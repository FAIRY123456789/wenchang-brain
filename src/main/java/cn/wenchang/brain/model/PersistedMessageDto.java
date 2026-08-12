package cn.wenchang.brain.model;

import cn.wenchang.brain.persistence.MessageRole;

import java.time.Instant;

public record PersistedMessageDto(
        Long id,
        MessageRole role,
        String content,
        Instant createdAt,
        String traceId,
        String modelProvider,
        String modelName,
        String sourcesJson,
        String toolsUsedJson,
        String agentId,
        String skillId,
        String agentRunJson,
        String artifactsJson
) {
    public String toolsJson() { return toolsUsedJson; }
}
