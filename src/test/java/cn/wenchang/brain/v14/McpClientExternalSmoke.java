package cn.wenchang.brain.v14;

import cn.wenchang.brain.agent.WenchangToolRegistry;
import cn.wenchang.brain.eval.AgentExperienceEvalService;
import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.service.WenchangAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 需要先在 127.0.0.1:8091 启动独立 MCP Server，再通过
 * {@code -Dtest=cn.wenchang.brain.v14.McpClientExternalSmoke test} 显式执行。
 */
@ActiveProfiles("mcp")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:v14-mcp-client;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.ai.default.api-key=",
        "wenchang.vector-store-file=target/v14-mcp-client/vector.json",
        "wenchang.trace-file=target/v14-mcp-client/trace.jsonl",
        "wenchang.web-search.enabled=false"
})
class McpClientExternalSmoke {

    @Autowired WenchangToolRegistry registry;
    @Autowired WenchangAgentService agentService;
    @Autowired AgentExperienceEvalService evalService;

    @Test
    void mainApplicationDiscoversAndCallsExternalMcpToolThroughAgentRun() {
        assertThat(registry.mcpToolNames()).contains(
                "searchPublicServices", "searchTownshipProfile", "searchStudyTourPlaces");
        String directResult = registry.invoke("searchPublicServices",
                Map.of("keyword", "", "category", "文化", "town", "文城镇"), "v14-mcp-direct");
        assertThat(directResult).contains("文昌市图书馆", "sourceId", "sourceUrl");

        ChatResponseDto response = agentService.chat("查询文城镇的公共文化设施", "v14-mcp-chat",
                "wenchang", "public-service");
        assertThat(response.answer()).isNotBlank();
        assertThat(response.toolsUsed()).contains("searchPublicServices");
        assertThat(response.agentRun()).isNotNull();
        assertThat(response.agentRun().steps())
                .anySatisfy(step -> {
                    assertThat(step.toolName()).isEqualTo("searchPublicServices");
                    assertThat(step.status()).isEqualTo("completed");
                });
    }

    @Test
    void agentExperienceEvalPassesWithRealMcpConnection() throws Exception {
        var report = evalService.run();
        assertThat(report.passed()).isEqualTo(7);
        assertThat(report.failed()).isZero();
        assertThat(report.results()).hasSize(7);
    }
}
