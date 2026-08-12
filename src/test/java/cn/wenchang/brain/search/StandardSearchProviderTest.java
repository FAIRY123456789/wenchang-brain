package cn.wenchang.brain.search;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.tool.WebSearchResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardSearchProviderTest {

    @Test
    void parsesOfficialTavilyAndBraveJsonContracts() throws Exception {
        TavilySearchProvider tavily = new TavilySearchProvider("https://example.test", "secret", 2);
        List<WebSearchResult> tavilyResults = tavily.parseResponse("""
                {"results":[{"title":"文昌新闻","url":"https://wenchang.gov.cn/a","content":"摘要"}]}
                """, 10);
        BraveSearchProvider brave = new BraveSearchProvider("https://example.test", "secret", 2);
        List<WebSearchResult> braveResults = brave.parseResponse("""
                {"web":{"results":[{"title":"官方发布","url":"https://hainan.gov.cn/b","description":"内容"}]}}
                """, 10);

        assertThat(tavilyResults).containsExactly(new WebSearchResult(
                "文昌新闻", "https://wenchang.gov.cn/a", "摘要", "", "tavily"));
        assertThat(braveResults).containsExactly(new WebSearchResult(
                "官方发布", "https://hainan.gov.cn/b", "内容", "", "brave"));
    }

    @Test
    void failsOverCachesAndOpensCircuitWithoutRetryingBrokenProvider() {
        AtomicInteger brokenCalls = new AtomicInteger();
        AtomicInteger healthyCalls = new AtomicInteger();
        SearchProvider broken = fake("broken", brokenCalls, true);
        SearchProvider healthy = fake("healthy", healthyCalls, false);
        ResilientSearchProvider provider = new ResilientSearchProvider(List.of(broken, healthy),
                Duration.ofMinutes(5), 1, Duration.ofMinutes(1));

        List<WebSearchResult> first = provider.search("文昌最新新闻", 10);
        List<WebSearchResult> cached = provider.search("文昌最新新闻", 10);
        List<WebSearchResult> another = provider.search("文昌航天", 10);

        assertThat(first).hasSize(1);
        assertThat(cached).isEqualTo(first);
        assertThat(another).hasSize(1);
        assertThat(brokenCalls).hasValue(1);
        assertThat(healthyCalls).hasValue(2);
        assertThat(provider.currentHealth().available()).isTrue();
    }

    @Test
    void autoModeRequiresAStandardApiKeyAndDoesNotSilentlyScrapeHtml() {
        WenchangProperties properties = new WenchangProperties();
        SearchProvider provider = new SearchProviderConfiguration().searchProvider(properties);

        assertThat(provider.id()).isEqualTo("auto");
        assertThat(provider.currentHealth().errorType()).isEqualTo("SEARCH_API_NOT_CONFIGURED");
        assertThatThrownBy(() -> provider.search("文昌新闻", 10))
                .isInstanceOf(SearchProviderException.class)
                .hasMessageContaining("WENCHANG_TAVILY_API_KEY");
    }

    @Test
    void explicitlyConfiguredHtmlFallbackRemainsOptIn() {
        WenchangProperties properties = new WenchangProperties();
        properties.getWebSearch().setAllowHtmlFallback(true);
        properties.getWebSearch().setFallbackOrder("sogou");

        SearchProvider provider = new SearchProviderConfiguration().searchProvider(properties);

        assertThat(provider.id()).contains("sogou");
    }

    private SearchProvider fake(String id, AtomicInteger calls, boolean fails) {
        return new SearchProvider() {
            @Override public String id() { return id; }
            @Override public List<WebSearchResult> search(String query, int limit) {
                calls.incrementAndGet();
                if (fails) throw new SearchProviderException("UPSTREAM_ERROR", "temporary");
                return List.of(new WebSearchResult("结果", "https://example.cn/" + query.hashCode(), "摘要"));
            }
            @Override public SearchProviderHealth healthCheck() {
                return new SearchProviderHealth(id, fails ? "UNAVAILABLE" : "AVAILABLE", 1,
                        fails ? null : Instant.now(), fails ? "temporary" : "", fails ? "UPSTREAM_ERROR" : "");
            }
            @Override public SearchProviderHealth currentHealth() { return healthCheck(); }
        };
    }
}
