package cn.wenchang.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/** 命令行生产冒烟客户端；不会被 Surefire 自动当作测试执行。 */
public final class McpExternalSmoke {

    private McpExternalSmoke() {}

    public static void main(String[] args) {
        String baseUrl = args.length == 0 ? "http://127.0.0.1:8091" : args[0];
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint("/mcp")
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        try (McpSyncClient client = McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(15))
                .build()) {
            McpSchema.InitializeResult initialized = client.initialize();
            System.out.println("SERVER=" + initialized.serverInfo().name() + "@" + initialized.serverInfo().version());
            String tools = client.listTools().tools().stream().map(McpSchema.Tool::name).sorted()
                    .collect(Collectors.joining(","));
            System.out.println("TOOLS=" + tools);
            call(client, "searchPublicServices", Map.of("category", "文化", "town", "文城"));
            call(client, "searchTownshipProfile", Map.of("town", "龙楼镇"));
            call(client, "searchStudyTourPlaces", Map.of("theme", "航天", "town", "龙楼"));
            call(client, "createWenchangWordReport", Map.of(
                    "title", "文昌生产型 Agent 验收报告", "topic", "MCP Artifact",
                    "content", "## 验收结果\n真实 MCP Tool 已生成 Word。\n- 中文正文\n1. 生产链路通过",
                    "sources", List.of("文昌市人民政府 - https://wenchang.hainan.gov.cn/"),
                    "conversationId", "production-acceptance", "createdByAgent", "wenchang",
                    "skillId", "word-report"));
            call(client, "exportWenchangData", Map.of(
                    "datasetType", "places", "fields", List.of("name", "town", "sourceUrl"),
                    "filters", Map.of("town", "龙楼"), "format", "xlsx",
                    "conversationId", "production-acceptance", "createdByAgent", "study-tour",
                    "skillId", "data-export"));
            call(client, "exportWenchangData", Map.of(
                    "datasetType", "policies", "fields", List.of("title", "organization", "publishedAt", "sourceUrl"),
                    "filters", Map.of(), "format", "csv", "conversationId", "production-acceptance",
                    "createdByAgent", "policy", "skillId", "data-export"));
            call(client, "createStudyTourPackage", Map.of(
                    "ageGroup", "初二", "duration", "一天", "themes", List.of("航天", "生态"),
                    "preferences", List.of("科普", "安全"), "conversationId", "production-acceptance",
                    "createdByAgent", "study-tour", "skillId", "study-tour-package"));
            call(client, "createPolicyBrief", Map.of(
                    "topic", "文昌商业航天", "timeRange", "2024-2026", "focus", "产业发展",
                    "conversationId", "production-acceptance", "createdByAgent", "policy",
                    "skillId", "policy-brief"));
        }
    }

    private static void call(McpSyncClient client, String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest.builder(name)
                .arguments(arguments).build());
        String summary = result.toString().replaceAll("\\s+", " ");
        if (summary.length() > 500) summary = summary.substring(0, 500) + "...";
        System.out.println("CALL=" + name + ";ERROR=" + Boolean.TRUE.equals(result.isError()) + ";RESULT=" + summary);
    }
}
