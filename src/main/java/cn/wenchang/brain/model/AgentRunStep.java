package cn.wenchang.brain.model;

import java.util.List;

public record AgentRunStep(
        String id,
        String label,
        String type,
        String toolName,
        String status,
        long latencyMs,
        int sourceCount,
        String toolSource,
        String summary,
        String errorType,
        String errorMessage,
        String inputPreview,
        List<String> artifactIds
) {
    public AgentRunStep {
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
    }

    public AgentRunStep(String id, String label, String type, String toolName, String status,
                        long latencyMs, int sourceCount, String toolSource, String summary,
                        String errorType, String errorMessage, String inputPreview) {
        this(id, label, type, toolName, status, latencyMs, sourceCount, toolSource, summary,
                errorType, errorMessage, inputPreview, List.of());
    }

    public AgentRunStep(String id, String label, String type, String toolName, String status,
                        long latencyMs, int sourceCount) {
        this(id, label, type, toolName, status, latencyMs, sourceCount, null, null, null, null, null, List.of());
    }
}
