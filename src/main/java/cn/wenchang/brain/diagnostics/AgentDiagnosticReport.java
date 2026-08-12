package cn.wenchang.brain.diagnostics;

import cn.wenchang.brain.search.SearchProviderHealth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentDiagnosticReport(
        Instant checkedAt,
        ModelDiagnostic model,
        ToolDiagnostic nativeTools,
        McpDiagnostic mcp,
        SearchDiagnostic search,
        RagDiagnostic rag,
        ArtifactDiagnostic artifact
) {
    public record ModelDiagnostic(String provider, String model, boolean apiKeyConfigured,
                                  boolean connected, long latencyMs, boolean toolCallingSupported,
                                  String traceId, String status, String errorType) { }
    public record ToolDiagnostic(List<String> available, Map<String, Check> tested) { }
    public record McpDiagnostic(List<String> servers, boolean connected, List<String> tools) { }
    public record SearchDiagnostic(SearchProviderHealth webSearch, Check officialSearch) { }
    public record RagDiagnostic(boolean ready, int documents, int chunks) { }
    public record ArtifactDiagnostic(Check word, Check dataExport) { }
    public record Check(String status, long latencyMs, String errorType, String detail) {
        public static Check available(long latencyMs, String detail) {
            return new Check("AVAILABLE", latencyMs, "", detail);
        }
        public static Check unavailable(long latencyMs, String errorType, String detail) {
            return new Check("UNAVAILABLE", latencyMs, errorType, detail);
        }
    }
}
