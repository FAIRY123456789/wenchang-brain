package cn.wenchang.brain.model;

import java.time.Instant;
import java.util.List;

public record AgentTrace(
        String traceId,
        Instant timestamp,
        String sessionId,
        String query,
        boolean ragExecuted,
        List<RetrievedChunk> retrievedChunks,
        List<ToolCallTrace> toolCalls,
        long ragLatencyMs,
        long llmLatencyMs,
        long totalLatencyMs,
        List<SourceRef> sources,
        String modelMode,
        String modelProvider,
        String modelName,
        String answerPreview,
        String error
) { }
