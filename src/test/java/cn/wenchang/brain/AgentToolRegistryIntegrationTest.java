package cn.wenchang.brain;

import cn.wenchang.brain.agent.WenchangToolRegistry;
import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.controller.AgentToolsController;
import cn.wenchang.brain.mcp.McpToolProviderAdapter;
import cn.wenchang.brain.rag.RagService;
import cn.wenchang.brain.tool.KnowledgeEvidenceTool;
import cn.wenchang.brain.tool.OfficialSourceRegistry;
import cn.wenchang.brain.tool.OfficialSourceSearchTool;
import cn.wenchang.brain.tool.PlaceSearchTool;
import cn.wenchang.brain.tool.PolicySearchTool;
import cn.wenchang.brain.tool.WebSearchTool;
import cn.wenchang.brain.tool.CollectOfficialMaterialsTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolRegistryIntegrationTest {

    @Test
    void exposesFiveNativeToolsAndNoMcpToolsWhenMcpIsDisabled() {
        WenchangProperties properties = new WenchangProperties();
        WebSearchTool web = new WebSearchTool(properties);
        OfficialSourceRegistry sourceRegistry = mock(OfficialSourceRegistry.class);
        RagService ragService = mock(RagService.class);
        McpToolProviderAdapter mcp = mock(McpToolProviderAdapter.class);
        when(mcp.discoverTools()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        OfficialSourceSearchTool official = new OfficialSourceSearchTool(sourceRegistry, web);
        properties.setResearchDir("target/test-research");
        WenchangToolRegistry registry = new WenchangToolRegistry(web,
                official, new KnowledgeEvidenceTool(ragService),
                new PlaceSearchTool("target/missing-places.json"),
                new PolicySearchTool("target/missing-policies.json"),
                new CollectOfficialMaterialsTool(official, web, properties), mcp);
        AgentToolsController controller = new AgentToolsController(registry);

        WenchangToolRegistry.ToolCatalog catalog = controller.tools();
        assertThat(catalog.nativeTools()).extracting(WenchangToolRegistry.ToolDescriptor::name).containsExactly(
                "webSearch", "officialSourceSearch", "knowledgeEvidence", "placeSearch", "policySearch",
                "collectOfficialMaterials");
        assertThat(catalog.nativeTools()).allSatisfy(tool -> {
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.source()).isEqualTo("NATIVE");
        });
        assertThat(catalog.mcpTools()).isEmpty();
    }
}
