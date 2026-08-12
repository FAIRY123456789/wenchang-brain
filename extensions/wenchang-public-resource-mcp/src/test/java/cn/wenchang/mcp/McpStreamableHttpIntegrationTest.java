package cn.wenchang.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "wenchang.public-resource.data-root=src/test/resources/fixtures",
        "wenchang.artifact.root=target/test-artifacts/mcp-integration"
})
class McpStreamableHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void initializesListsAndCallsToolsOverRealStreamableHttp() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            McpSchema.InitializeResult initializeResult = client.initialize();
            assertThat(initializeResult.serverInfo().name()).isEqualTo("wenchang-public-resource-mcp");

            McpSchema.ListToolsResult listResult = client.listTools();
            Set<String> names = listResult.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
            assertThat(names).containsExactlyInAnyOrder(
                    "searchPublicServices", "searchTownshipProfile", "searchStudyTourPlaces",
                    "createWenchangWordReport", "exportWenchangData", "createStudyTourPackage", "createPolicyBrief");

            McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                    .builder("searchPublicServices")
                    .arguments(Map.of("category", "文化", "town", "文城"))
                    .build());
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content()).isNotEmpty();
            assertThat(result.toString()).contains("文昌市图书馆");
            assertThat(result.toString()).contains("SRC-TEST-SERVICE-001");

            McpSchema.CallToolResult townshipResult = client.callTool(McpSchema.CallToolRequest
                    .builder("searchTownshipProfile")
                    .arguments(Map.of("town", "龙楼镇"))
                    .build());
            assertThat(townshipResult.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(townshipResult.toString()).contains("龙楼镇");

            McpSchema.CallToolResult placeResult = client.callTool(McpSchema.CallToolRequest
                    .builder("searchStudyTourPlaces")
                    .arguments(Map.of("theme", "航天", "town", "龙楼", "ageGroup", "初中"))
                    .build());
            assertThat(placeResult.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(placeResult.toString()).contains("文昌航天科普中心");
            assertThat(placeResult.toString()).contains("SRC-TEST-PLACE-001");

            McpSchema.CallToolResult artifactResult = client.callTool(McpSchema.CallToolRequest
                    .builder("createWenchangWordReport")
                    .arguments(Map.of("title", "文昌 MCP 中文报告", "topic", "协议验收",
                            "content", "## 正文\n真实 MCP 工具已执行。", "sources", java.util.List.of("https://example.test/source"),
                            "conversationId", "mcp-integration"))
                    .build());
            assertThat(artifactResult.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(artifactResult.toString()).contains("artifactId", ".docx", "/api/artifacts/");
        }
    }
}
