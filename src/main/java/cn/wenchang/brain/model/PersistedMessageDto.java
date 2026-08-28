package cn.wenchang.brain.model;

import cn.wenchang.brain.persistence.MessageRole;

import java.time.Instant;
import java.util.List;

public record PersistedMessageDto(
        Long id,
        MessageRole role,
        String content,
        Instant createdAt,
        Long parentMessageId,
        String revisionGroupId,
        Integer revisionIndex,
        List<MessageRevisionOption> revisions,
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
    public PersistedMessageDto {
        revisions = revisions == null ? List.of() : List.copyOf(revisions);
    }

    public String toolsJson() { return toolsUsedJson; }
    public int revisionCount() { return revisions.size(); }
}