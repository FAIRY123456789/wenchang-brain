package cn.wenchang.brain.search;

import cn.wenchang.brain.tool.WebSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChineseSearchOptimizationTest {

    private final ChineseSearchQueryRewriter rewriter = new ChineseSearchQueryRewriter();
    private final SearchResultRanker ranker = new SearchResultRanker();

    @Test
    void removesConversationalInstructionsAndAddsHainanRecencyContext() {
        var intent = rewriter.rewrite("请只调用一次联网搜索，搜索“文昌市人民政府 最新动态”，"
                + "基于真实搜索结果简短列出前2条标题和链接，不要生成长篇回答。");

        assertThat(intent.rewrittenQuery()).isEqualTo(
                "海南 文昌市人民政府 最新动态 新闻 2026 site:hainan.gov.cn");
        assertThat(intent.wenchang()).isTrue();
        assertThat(intent.recent()).isTrue();
        assertThat(intent.official()).isTrue();
        assertThat(intent.requestedDomains()).containsExactly("hainan.gov.cn");
        assertThat(intent.rewrittenQuery()).contains("site:hainan.gov.cn");
    }

    @Test
    void preservesSiteQualifierForProviderSideDomainFiltering() {
        var intent = rewriter.rewrite("文昌航天最新进展 site:cmse.gov.cn");

        assertThat(intent.rewrittenQuery()).contains("海南", "文昌航天最新进展", "2026", "site:cmse.gov.cn");
        assertThat(intent.requestedDomains()).containsExactly("cmse.gov.cn");
    }

    @Test
    void removesTainanAndRanksOfficialHainanSourceBeforeSocialContent() {
        var intent = rewriter.rewrite("文昌市人民政府 最新动态");
        List<WebSearchResult> ranked = ranker.rank(intent, List.of(
                new WebSearchResult("臺南市政府施政计划", "https://tainan.gov.tw/a", "臺南市政府", "2026-08-11", "tavily"),
                new WebSearchResult("文昌研学活动", "https://instagram.com/p/1", "海南文昌旅游宣传", "2026-08-11", "tavily"),
                new WebSearchResult("文昌市有关工作最新消息", "https://www.hainan.gov.cn/hainan/xgwjt/a.shtml",
                        "海南省人民政府发布文昌市工作动态", "2026-08-10", "tavily")), 10);

        assertThat(ranked).extracting(WebSearchResult::url)
                .containsExactly("https://www.hainan.gov.cn/hainan/xgwjt/a.shtml", "https://instagram.com/p/1");
    }

    @Test
    void filtersResultsThatOnlyMatchAPlaceNameOutsideWenchangIntent() {
        var intent = rewriter.rewrite("文昌最新新闻");
        List<WebSearchResult> ranked = ranker.rank(intent, List.of(
                new WebSearchResult("北京文昌路施工", "https://example.com/beijing", "北京道路新闻", "", "tavily"),
                new WebSearchResult("海南文昌发布新动态", "https://news.example.com/wenchang", "文昌市最新消息", "", "tavily")), 10);

        assertThat(ranked).extracting(WebSearchResult::title).containsExactly("海南文昌发布新动态");
    }

    @Test
    void dropsOpaqueSearchEngineRedirectWrappers() {
        var intent = rewriter.rewrite("文昌最新新闻");
        List<WebSearchResult> ranked = ranker.rank(intent, List.of(
                new WebSearchResult("海南文昌动态", "https://www.google.com/goto?url=opaque",
                        "文昌市新闻", "2026-08-11", "tavily"),
                new WebSearchResult("海南文昌官方动态", "https://www.hainan.gov.cn/hainan/news/a.shtml",
                        "文昌市人民政府工作消息", "2026-08-11", "tavily")), 10);

        assertThat(ranked).extracting(WebSearchResult::url)
                .containsExactly("https://www.hainan.gov.cn/hainan/news/a.shtml");
    }
}
