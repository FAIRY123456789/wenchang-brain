package cn.wenchang.brain.tool;

import cn.wenchang.brain.config.WenchangProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectOfficialMaterialsToolTest {

    @Test
    void persistsConversationScopedResearchDataset() throws Exception {
        Path temporary = Path.of("target", "test-research-" + UUID.randomUUID()).toAbsolutePath();
        OfficialSourceSearchTool official = mock(OfficialSourceSearchTool.class);
        when(official.officialSourceSearch("商业航天")).thenReturn("""
                {"results":[{"title":"官方材料","url":"https://example.invalid/policy",
                "snippet":"发布于2026-08-01","sourceOrganization":"测试机构"}],"message":""}
                """);
        WebSearchTool web = mock(WebSearchTool.class);
        WenchangProperties properties = new WenchangProperties();
        properties.setResearchDir(temporary.toString());
        CollectOfficialMaterialsTool tool = new CollectOfficialMaterialsTool(official, web, properties);

        String output = tool.callback().call("{\"topic\":\"商业航天\",\"categories\":[\"政策\"],\"maxSources\":1}",
                new ToolContext(Map.of("wenchang.conversationId", "conversation-123")));
        var json = new ObjectMapper().readTree(output);

        assertThat(json.path("sourceCount").asInt()).isEqualTo(1);
        Path saved = temporary.resolve("conversation-123").resolve(json.path("datasetId").asText() + ".json");
        assertThat(saved).isRegularFile();
        assertThat(Files.readString(saved)).contains("官方材料", "conversation-123", "2026-08-01");
    }
}
