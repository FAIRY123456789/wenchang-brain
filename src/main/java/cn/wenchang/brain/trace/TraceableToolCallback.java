package cn.wenchang.brain.trace;

import cn.wenchang.brain.model.ToolCallTrace;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为任意 Native 或 MCP ToolCallback 增加一致的 Trace 行为。
 *
 * <p>装饰器不改变工具 JSON Schema、元数据或返回值，只测量执行耗时并把输入、输出归属到
 * 当前 Agent traceId。traceId 通过 ChatClient.toolContext() 传递，因而流式换线程时仍然有效。</p>
 */
public final class TraceableToolCallback implements ToolCallback {

    private static final int TRACE_OUTPUT_LIMIT = 3_000;
    private static final Pattern ERROR_TYPE = Pattern.compile("(?i)errorType[=:]\\s*([A-Za-z0-9_-]+)");
    private final ToolCallback delegate;
    private final String source;

    public TraceableToolCallback(ToolCallback delegate) {
        this(delegate, "native");
    }

    public TraceableToolCallback(ToolCallback delegate, String source) {
        this.delegate = delegate;
        this.source = source == null || source.isBlank() ? "NATIVE" : source.toUpperCase(Locale.ROOT);
    }

    @Override
    public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }

    @Override
    public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }

    @Override
    public String call(String toolInput) {
        return tracedCall(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return tracedCall(toolInput, toolContext);
    }

    private String tracedCall(String input, ToolContext context) {
        String traceId = traceId(context);
        long started = System.nanoTime();
        String output = "";
        String status = "SUCCESS";
        String errorType = "";
        try {
            output = context == null ? delegate.call(input) : delegate.call(input, context);
            Failure failure = classifyOutput(output);
            status = failure.failed() ? "FAILED" : "SUCCESS";
            errorType = failure.errorType();
            return output;
        } catch (RuntimeException exception) {
            output = "ERROR " + exception.getClass().getSimpleName() + ": " + safe(exception.getMessage());
            status = "FAILED";
            errorType = exception.getClass().getSimpleName();
            throw exception;
        } finally {
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            ToolTraceCollector.record(traceId, new ToolCallTrace(traceId, getToolDefinition().name(),
                    source, stage(context), truncate(input), status, errorType, truncate(output), latencyMs));
        }
    }

    private String stage(ToolContext context) {
        if (context != null && context.getContext() != null) {
            Object stage = context.getContext().get(ToolTraceCollector.STAGE_CONTEXT_KEY);
            if (stage != null && !String.valueOf(stage).isBlank()) return String.valueOf(stage);
        }
        return "TOOL_EXECUTION";
    }

    private Failure classifyOutput(String output) {
        if (output == null) return new Failure(false, "");
        String lower = output.toLowerCase(Locale.ROOT);
        boolean failed = lower.contains("status=unavailable") || lower.contains("联网搜索失败")
                || lower.contains("联网搜索不可用") || lower.startsWith("error ")
                || lower.contains("\"iserror\":true");
        if (!failed) return new Failure(false, "");
        Matcher matcher = ERROR_TYPE.matcher(output);
        if (matcher.find()) return new Failure(true, matcher.group(1).toUpperCase(Locale.ROOT));
        if (lower.contains("timeout") || lower.contains("超时")) return new Failure(true, "TIMEOUT");
        if (lower.contains("http 302") || lower.contains("antispider") || lower.contains("anti-bot")) {
            return new Failure(true, "ANTI_BOT");
        }
        return new Failure(true, "TOOL_ERROR");
    }

    private String traceId(ToolContext context) {
        if (context != null) {
            Map<String, Object> values = context.getContext();
            Object value = values == null ? null : values.get(ToolTraceCollector.TRACE_ID_CONTEXT_KEY);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return ToolTraceCollector.traceId();
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() <= TRACE_OUTPUT_LIMIT ? value : value.substring(0, TRACE_OUTPUT_LIMIT) + "…";
    }

    private record Failure(boolean failed, String errorType) { }
}
