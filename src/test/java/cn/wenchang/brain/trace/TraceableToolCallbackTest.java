package cn.wenchang.brain.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TraceableToolCallbackTest {

    private static final String TRACE_ID = "trace-across-tool-context";

    @AfterEach
    void clear() { ToolTraceCollector.clear(TRACE_ID); }

    @Test
    void recordsToolUsingTraceIdFromToolContext() throws Exception {
        ToolCallback delegate = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("sampleTool").description("sample")
                        .inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String input) { return "result:" + input; }
        };
        ToolTraceCollector.begin(TRACE_ID);
        TraceableToolCallback callback = new TraceableToolCallback(delegate);

        // 异步线程没有调用 begin()，只能依靠 ToolContext 中的 traceId 找到主请求上下文。
        String result = CompletableFuture.supplyAsync(() -> callback.call("{\"query\":\"文昌\"}",
                new ToolContext(Map.of(ToolTraceCollector.TRACE_ID_CONTEXT_KEY, TRACE_ID))))
                .get(5, TimeUnit.SECONDS);

        assertThat(result).contains("文昌");
        assertThat(ToolTraceCollector.snapshot(TRACE_ID)).singleElement().satisfies(trace -> {
            assertThat(trace.toolName()).isEqualTo("sampleTool");
            assertThat(trace.toolSource()).isEqualTo("NATIVE");
            assertThat(trace.stage()).isEqualTo("TOOL_EXECUTION");
            assertThat(trace.status()).isEqualTo("SUCCESS");
            assertThat(trace.errorType()).isEmpty();
            assertThat(trace.toolInput()).contains("文昌");
            assertThat(trace.toolOutput()).contains("result");
        });
    }

    @Test
    void classifiesReturnedUnavailableTextAsFailedWithoutRequiringException() {
        ToolCallback delegate = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("webSearch").description("sample")
                        .inputSchema("{\"type\":\"object\"}").build();
            }
            @Override public String call(String input) {
                return "联网搜索不可用：provider=sogou; status=UNAVAILABLE; errorType=ANTI_BOT";
            }
        };
        ToolTraceCollector.begin(TRACE_ID);
        new TraceableToolCallback(delegate, "native").call("{}",
                new ToolContext(Map.of(ToolTraceCollector.TRACE_ID_CONTEXT_KEY, TRACE_ID,
                        ToolTraceCollector.STAGE_CONTEXT_KEY, "SEARCH")));

        assertThat(ToolTraceCollector.snapshot(TRACE_ID)).singleElement().satisfies(trace -> {
            assertThat(trace.toolSource()).isEqualTo("NATIVE");
            assertThat(trace.stage()).isEqualTo("SEARCH");
            assertThat(trace.status()).isEqualTo("FAILED");
            assertThat(trace.errorType()).isEqualTo("ANTI_BOT");
        });
    }

    @Test
    void mcpArtifactCallUsesAuthoritativeConversationContextAndCollectsArtifactId() {
        java.util.concurrent.atomic.AtomicReference<String> actualInput = new java.util.concurrent.atomic.AtomicReference<>();
        ToolCallback delegate = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("createWenchangWordReport").description("artifact")
                        .inputSchema("{\"type\":\"object\"}").build();
            }
            @Override public String call(String input) {
                actualInput.set(input);
                return "[{\"type\":\"text\",\"text\":\"{\\\"artifactId\\\":\\\"artifact-123\\\","
                        + "\\\"filename\\\":\\\"report.docx\\\"}\"}]";
            }
        };
        ToolTraceCollector.begin(TRACE_ID);
        var context = new ToolContext(Map.of(
                ToolTraceCollector.TRACE_ID_CONTEXT_KEY, TRACE_ID,
                ToolTraceCollector.CONVERSATION_ID_CONTEXT_KEY, "real-conversation-uuid",
                ToolTraceCollector.AGENT_ID_CONTEXT_KEY, "wenchang",
                ToolTraceCollector.SKILL_ID_CONTEXT_KEY, "public-service"));

        new TraceableToolCallback(delegate, "MCP").call(
                "{\"conversationId\":\"model-invented-id\",\"createdByAgent\":\"wrong\"}", context);

        assertThat(actualInput.get()).contains("real-conversation-uuid", "wenchang", "public-service")
                .doesNotContain("model-invented-id", "wrong");
        assertThat(ToolTraceCollector.artifactIds(TRACE_ID)).containsExactly("artifact-123");
    }

    @Test
    void mcpQueryToolKeepsItsDeclaredSchemaInputUntouched() {
        java.util.concurrent.atomic.AtomicReference<String> actualInput = new java.util.concurrent.atomic.AtomicReference<>();
        ToolCallback delegate = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("searchPublicServices").description("query")
                        .inputSchema("{\"type\":\"object\",\"additionalProperties\":false}").build();
            }
            @Override public String call(String input) {
                actualInput.set(input);
                return "[]";
            }
        };
        var context = new ToolContext(Map.of(
                ToolTraceCollector.TRACE_ID_CONTEXT_KEY, TRACE_ID,
                ToolTraceCollector.CONVERSATION_ID_CONTEXT_KEY, "real-conversation-uuid"));

        new TraceableToolCallback(delegate, "MCP").call("{\"keyword\":\"高中\"}", context);

        assertThat(actualInput.get()).isEqualTo("{\"keyword\":\"高中\"}")
                .doesNotContain("conversationId");
    }
}
