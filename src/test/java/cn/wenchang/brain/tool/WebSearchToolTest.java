package cn.wenchang.brain.tool;

import cn.wenchang.brain.config.WenchangProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchToolTest {

    @Test
    void parsesSogouHtmlIntoTypedResults() {
        WenchangProperties properties = new WenchangProperties();
        properties.getWebSearch().setEndpoint("https://www.sogou.com/web");
        WebSearchTool tool = new WebSearchTool(properties);
        String html = """
                <html><body>
                  <h3 class="vr-title"><a href="/link?url=abc"><em>文昌</em>官方公告</a></h3>
                  <div class="fz-mid">官方公告摘要 &amp; 说明</div>
                </body></html>
                """;

        List<WebSearchResult> results = tool.parseHtml(html, URI.create("https://www.sogou.com/web"), 6);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("文昌 官方公告");
        assertThat(results.get(0).url()).isEqualTo("https://www.sogou.com/link?url=abc");
        assertThat(results.get(0).snippet()).contains("官方公告摘要 & 说明");
    }
}
