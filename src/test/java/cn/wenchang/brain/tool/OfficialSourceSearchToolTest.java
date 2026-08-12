package cn.wenchang.brain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfficialSourceSearchToolTest {

    @Test
    void onlyReturnsUrlsWhoseFinalHostMatchesRegisteredDomain() throws Exception {
        OfficialSourceRegistry registry = mock(OfficialSourceRegistry.class);
        WebSearchTool web = mock(WebSearchTool.class);
        var source = new OfficialSourceRegistry.OfficialSource(
                "文昌市人民政府", "wenchang.gov.cn", "政府 政策", "P0", true);
        when(registry.candidates(anyString(), anyInt())).thenReturn(List.of(source));
        when(web.searchResults(anyString(), anyInt())).thenReturn(List.of(
                new WebSearchResult("合法", "https://wenchang.gov.cn/a", "摘要 A"),
                new WebSearchResult("合法子域", "https://data.wenchang.gov.cn/b", "摘要 B"),
                new WebSearchResult("伪装域名", "https://wenchang.gov.cn.evil.example/c", "摘要 C")));
        when(web.resolveExternalUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        OfficialSourceSearchTool tool = new OfficialSourceSearchTool(registry, web);

        JsonNode output = new ObjectMapper().readTree(tool.officialSourceSearch("查询文昌政府政策"));

        assertThat(output.path("results").size()).isEqualTo(2);
        assertThat(output.toString()).contains("文昌市人民政府", "data.wenchang.gov.cn")
                .doesNotContain("evil.example");
        assertThat(tool.isAllowed("https://notwenchang.gov.cn/a", source)).isFalse();
        var exactOnly = new OfficialSourceRegistry.OfficialSource(
                "统计局", "stats.hainan.gov.cn", "statistics", "P0", false);
        assertThat(tool.isAllowed("https://stats.hainan.gov.cn/a", exactOnly)).isTrue();
        assertThat(tool.isAllowed("https://data.stats.hainan.gov.cn/a", exactOnly)).isFalse();
    }
}
