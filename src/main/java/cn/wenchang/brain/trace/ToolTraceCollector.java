package cn.wenchang.brain.trace;

import cn.wenchang.brain.model.ToolCallTrace;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.LinkedHashSet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;

/**
 * 一次 Agent 请求的工具调用收集器。
 *
 * <p>Spring AI 2.0 的 ToolCallingAdvisor 在流式场景可能跨 Reactor 线程执行 ToolCallback，
 * 因此不能只依赖 ThreadLocal 保存调用列表。这里以 traceId 为主键保存并发安全的请求上下文；
 * ThreadLocal 只作为同步预路由工具的兼容回退。真正的模型工具调用会由
 * {@link TraceableToolCallback} 从 ToolContext 取得 traceId。</p>
 */
public final class ToolTraceCollector {

    public static final String TRACE_ID_CONTEXT_KEY = "wenchang.traceId";
    public static final String STAGE_CONTEXT_KEY = "wenchang.stage";
    public static final String CONVERSATION_ID_CONTEXT_KEY = "wenchang.conversationId";
    public static final String AGENT_ID_CONTEXT_KEY = "wenchang.agentId";
    public static final String SKILL_ID_CONTEXT_KEY = "wenchang.skillId";

    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();
    private static final Map<String, Context> ACTIVE = new ConcurrentHashMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ARTIFACT_ID = Pattern.compile(
            "(?i)(?:\\\"?artifactId\\\"?\\s*[:=]\\s*\\\"?)([A-Za-z0-9][A-Za-z0-9-]{0,63})");

    private ToolTraceCollector() { }

    public static void begin(String traceId) {
        ACTIVE.put(traceId, new Context(traceId, new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>()));
        CURRENT_TRACE_ID.set(traceId);
    }

    public static String traceId() {
        String traceId = CURRENT_TRACE_ID.get();
        return traceId == null ? "unbound" : traceId;
    }

    public static void record(ToolCallTrace call) {
        record(call.traceId(), call);
    }

    public static void record(String traceId, ToolCallTrace call) {
        Context context = ACTIVE.get(traceId);
        if (context != null) context.calls().add(call);
    }

    public static List<ToolCallTrace> snapshot() {
        return snapshot(traceId());
    }

    public static List<ToolCallTrace> snapshot(String traceId) {
        Context context = ACTIVE.get(traceId);
        return context == null ? List.of() : List.copyOf(context.calls());
    }

    public static void recordArtifactOutput(String traceId, String output) {
        Context context = ACTIVE.get(traceId);
        if (context == null || output == null || output.isBlank()) return;
        try {
            String id = findArtifactId(JSON.readTree(output));
            if (id != null && !id.isBlank()) context.artifactIds().add(id);
        } catch (Exception ignored) {
            var matcher = ARTIFACT_ID.matcher(output);
            if (matcher.find()) context.artifactIds().add(matcher.group(1));
        }
    }

    private static String findArtifactId(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            JsonNode direct = node.get("artifactId");
            if (direct != null && !direct.asText().isBlank()) return direct.asText();
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findArtifactId(fields.next().getValue());
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String found = findArtifactId(item);
                if (found != null) return found;
            }
        } else if (node.isTextual()) {
            String text = node.asText();
            try {
                if (text.startsWith("{") || text.startsWith("[")) {
                    String found = findArtifactId(JSON.readTree(text));
                    if (found != null) return found;
                }
            } catch (Exception ignored) { }
            var matcher = ARTIFACT_ID.matcher(text);
            if (matcher.find()) return matcher.group(1);
        }
        return null;
    }

    public static List<String> artifactIds(String traceId) {
        Context context = ACTIVE.get(traceId);
        return context == null ? List.of() : List.copyOf(new LinkedHashSet<>(context.artifactIds()));
    }

    public static void clear(String traceId) {
        ACTIVE.remove(traceId);
        if (traceId != null && traceId.equals(CURRENT_TRACE_ID.get())) CURRENT_TRACE_ID.remove();
    }

    public static void clear() { clear(traceId()); }

    private record Context(String traceId, List<ToolCallTrace> calls, List<String> artifactIds) { }
}
