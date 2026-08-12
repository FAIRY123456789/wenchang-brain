package cn.wenchang.brain.trace;

import cn.wenchang.brain.model.ToolCallTrace;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();
    private static final Map<String, Context> ACTIVE = new ConcurrentHashMap<>();

    private ToolTraceCollector() { }

    public static void begin(String traceId) {
        ACTIVE.put(traceId, new Context(traceId, new CopyOnWriteArrayList<>()));
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

    public static void clear(String traceId) {
        ACTIVE.remove(traceId);
        if (traceId != null && traceId.equals(CURRENT_TRACE_ID.get())) CURRENT_TRACE_ID.remove();
    }

    public static void clear() { clear(traceId()); }

    private record Context(String traceId, List<ToolCallTrace> calls) { }
}
