package cn.wenchang.brain.model;

import java.time.Instant;

public record PersistedAgentStep(
        Long id, int sequence, String name, String stage, String toolName, String toolSource,
        String status, Instant startedAt, Instant completedAt, long latencyMs, String summary,
        String errorType, String errorMessage, String inputPreview, String artifactIdsJson
) { }
