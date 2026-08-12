package cn.wenchang.brain.diagnostics;

import cn.wenchang.brain.agent.WenchangToolRegistry;
import cn.wenchang.brain.model.AgentTrace;
import cn.wenchang.brain.model.ToolCallTrace;
import cn.wenchang.brain.rag.KnowledgeService;
import cn.wenchang.brain.runtime.RuntimeChatModelProvider;
import cn.wenchang.brain.search.SearchProviderHealth;
import cn.wenchang.brain.tool.OfficialSourceSearchTool;
import cn.wenchang.brain.tool.WebSearchTool;
import cn.wenchang.brain.trace.ToolTraceCollector;
import cn.wenchang.brain.trace.TraceService;
import cn.wenchang.brain.trace.TraceableToolCallback;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 真实执行模型、搜索、MCP 发现、RAG 与文件能力探针的统一自检。 */
@Service
public class AgentDiagnosticsService {

    private final RuntimeChatModelProvider modelProvider;
    private final WenchangToolRegistry tools;
    private final WebSearchTool webSearch;
    private final OfficialSourceSearchTool officialSearch;
    private final KnowledgeService knowledge;
    private final TraceService traceService;
    private final Environment environment;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public AgentDiagnosticsService(RuntimeChatModelProvider modelProvider, WenchangToolRegistry tools,
                                   WebSearchTool webSearch, OfficialSourceSearchTool officialSearch,
                                   KnowledgeService knowledge, TraceService traceService,
                                   Environment environment) {
        this.modelProvider = modelProvider;
        this.tools = tools;
        this.webSearch = webSearch;
        this.officialSearch = officialSearch;
        this.knowledge = knowledge;
        this.traceService = traceService;
        this.environment = environment;
    }

    public AgentDiagnosticReport run() {
        ModelProbe probe = probeModelAndToolCalling();
        SearchProviderHealth web = webSearch.healthCheck();
        AgentDiagnosticReport.Check official = probeOfficialSearch(web);
        var knowledgeStatus = knowledge.getStatus();
        List<String> nativeNames = tools.nativeToolNames();
        List<String> mcpNames = tools.mcpToolNames();

        Map<String, AgentDiagnosticReport.Check> tested = new LinkedHashMap<>();
        tested.put("diagnosticEcho", probe.toolCallingSupported()
                ? AgentDiagnosticReport.Check.available(probe.latencyMs(), "DeepSeek → ToolCallback → Final Answer")
                : AgentDiagnosticReport.Check.unavailable(probe.latencyMs(), probe.errorType(),
                "诊断工具未完成真实调用"));
        tested.put("webSearch", web.available()
                ? AgentDiagnosticReport.Check.available(web.latencyMs(), web.provider())
                : AgentDiagnosticReport.Check.unavailable(web.latencyMs(), web.errorType(), web.lastError()));
        tested.put("officialSourceSearch", official);

        AgentDiagnosticReport.Check word = artifactCheck(mcpNames,
                List.of("createWenchangWordReport", "createStudyTourPackage", "createPolicyBrief"));
        AgentDiagnosticReport.Check data = artifactCheck(mcpNames, List.of("exportWenchangData"));

        return new AgentDiagnosticReport(Instant.now(),
                new AgentDiagnosticReport.ModelDiagnostic(probe.provider(), probe.model(),
                        modelProvider.settingsStatus().apiKeyConfigured(), probe.connected(), probe.latencyMs(),
                        probe.toolCallingSupported(), probe.traceId(), probe.connected() ? "AVAILABLE" : "UNAVAILABLE",
                        probe.errorType()),
                new AgentDiagnosticReport.ToolDiagnostic(nativeNames, Map.copyOf(tested)),
                new AgentDiagnosticReport.McpDiagnostic(mcpServers(), !mcpNames.isEmpty(), mcpNames),
                new AgentDiagnosticReport.SearchDiagnostic(web, official),
                new AgentDiagnosticReport.RagDiagnostic(
                        "READY".equals(knowledgeStatus.state()) || "LOADED".equals(knowledgeStatus.state()),
                        knowledgeStatus.documents(), knowledgeStatus.chunks()),
                new AgentDiagnosticReport.ArtifactDiagnostic(word, data));
    }

    private ModelProbe probeModelAndToolCalling() {
        var handle = modelProvider.current();
        var descriptor = handle.descriptor();
        String traceId = "diagnostic-" + UUID.randomUUID();
        if (!modelProvider.settingsStatus().configured()) {
            return new ModelProbe(descriptor.provider(), descriptor.model(), false, 0,
                    false, traceId, "MODEL_NOT_CONFIGURED");
        }
        DiagnosticEchoTool echo = new DiagnosticEchoTool();
        ToolCallback raw = ToolCallbacks.from(echo)[0];
        ToolCallback callback = new TraceableToolCallback(raw, "NATIVE");
        long started = System.nanoTime();
        String finalAnswer = "";
        String error = "";
        ToolTraceCollector.begin(traceId);
        try {
            finalAnswer = handle.chatClient().prompt()
                    .system("这是生产自检。必须调用 diagnosticEcho，message 必须是 hello；收到工具结果后只回复 DIAGNOSTIC_OK。")
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, traceId))
                    .tools(callback)
                    .toolContext(Map.of(ToolTraceCollector.TRACE_ID_CONTEXT_KEY, traceId,
                            ToolTraceCollector.STAGE_CONTEXT_KEY, "MODEL_TOOL_CALL"))
                    .user("请执行 diagnosticEcho({\"message\":\"hello\"}) 完成工具调用自检。")
                    .call().content();
            long latency = (System.nanoTime() - started) / 1_000_000;
            List<ToolCallTrace> calls = ToolTraceCollector.snapshot(traceId);
            boolean executed = echo.executed.get() && calls.stream().anyMatch(call ->
                    "diagnosticEcho".equals(call.toolName()) && "SUCCESS".equals(call.status()));
            persistDiagnosticTrace(traceId, descriptor, finalAnswer, "", calls, latency);
            return new ModelProbe(descriptor.provider(), descriptor.model(),
                    finalAnswer != null && !finalAnswer.isBlank(), latency, executed, traceId,
                    executed ? "" : "TOOL_CALL_NOT_EMITTED");
        } catch (Exception exception) {
            long latency = (System.nanoTime() - started) / 1_000_000;
            error = exception.getClass().getSimpleName() + ": " + safe(exception.getMessage());
            List<ToolCallTrace> calls = ToolTraceCollector.snapshot(traceId);
            persistDiagnosticTrace(traceId, descriptor, finalAnswer, error, calls, latency);
            return new ModelProbe(descriptor.provider(), descriptor.model(), false, latency,
                    false, traceId, exception.getClass().getSimpleName());
        } finally {
            ToolTraceCollector.clear(traceId);
        }
    }

    private void persistDiagnosticTrace(String traceId, RuntimeChatModelProvider.ModelDescriptor descriptor,
                                        String answer, String error, List<ToolCallTrace> calls, long latency) {
        traceService.append(new AgentTrace(traceId, Instant.now(), traceId, "[diagnostic] diagnosticEcho",
                false, List.of(), calls, 0, latency, latency, List.of(), descriptor.mode(),
                descriptor.provider(), descriptor.model(), preview(answer), error.isBlank() ? null : error));
    }

    private AgentDiagnosticReport.Check probeOfficialSearch(SearchProviderHealth web) {
        if (!web.available()) {
            return AgentDiagnosticReport.Check.unavailable(web.latencyMs(), web.errorType(),
                    "依赖的 " + web.provider() + " SearchProvider 不可用：" + web.lastError());
        }
        long started = System.nanoTime();
        try {
            JsonNode response = mapper.readTree(officialSearch.officialSourceSearch("文昌市最新政策"));
            long latency = (System.nanoTime() - started) / 1_000_000;
            int count = response.path("results").size();
            if (count > 0) return AgentDiagnosticReport.Check.available(latency, count + " 个已验证官方结果");
            return AgentDiagnosticReport.Check.unavailable(latency, "NO_VERIFIED_OFFICIAL_RESULTS",
                    response.path("message").asText("未找到已验证官方结果"));
        } catch (Exception exception) {
            return AgentDiagnosticReport.Check.unavailable((System.nanoTime() - started) / 1_000_000,
                    exception.getClass().getSimpleName(), safe(exception.getMessage()));
        }
    }

    private AgentDiagnosticReport.Check artifactCheck(List<String> toolNames, List<String> expected) {
        boolean available = toolNames.stream().anyMatch(actual -> expected.stream().anyMatch(name ->
                actual.equals(name) || actual.endsWith("_" + name)));
        return available ? AgentDiagnosticReport.Check.available(0, "MCP Tool 已发现")
                : AgentDiagnosticReport.Check.unavailable(0, "MCP_TOOL_NOT_DISCOVERED", "对应文件工具尚未发现");
    }

    private List<String> mcpServers() {
        String url = environment.getProperty(
                "spring.ai.mcp.client.streamable-http.connections.wenchang-public-resource.url", "");
        String endpoint = environment.getProperty(
                "spring.ai.mcp.client.streamable-http.connections.wenchang-public-resource.endpoint", "/mcp");
        if (!url.isBlank()) return List.of(url.replaceAll("/+$", "") + endpoint);
        return tools.mcpToolNames().isEmpty() ? List.of() : List.of("http://127.0.0.1:8091/mcp");
    }

    private String safe(String message) {
        if (message == null) return "unknown";
        return message.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }

    private String preview(String value) {
        if (value == null) return "";
        return value.length() > 180 ? value.substring(0, 180) + "…" : value;
    }

    private record ModelProbe(String provider, String model, boolean connected, long latencyMs,
                              boolean toolCallingSupported, String traceId, String errorType) { }

    public static final class DiagnosticEchoTool {
        private final AtomicBoolean executed = new AtomicBoolean();

        @Tool(name = "diagnosticEcho", description = "仅用于生产诊断；回显 message 并确认工具已执行。")
        public DiagnosticEchoResult diagnosticEcho(@ToolParam(description = "必须是 hello") String message) {
            executed.set(true);
            return new DiagnosticEchoResult(message, true);
        }
    }

    public record DiagnosticEchoResult(String message, boolean toolExecuted) { }
}
