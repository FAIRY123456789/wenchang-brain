package cn.wenchang.brain.model;

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
        String inputPreview
) {
    public AgentRunStep(String id, String label, String type, String toolName, String status,
                        long latencyMs, int sourceCount) {
        this(id, label, type, toolName, status, latencyMs, sourceCount, null, null, null, null, null);
    }
}
