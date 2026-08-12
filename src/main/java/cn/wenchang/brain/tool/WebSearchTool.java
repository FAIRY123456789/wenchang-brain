package cn.wenchang.brain.tool;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.search.SearchProvider;
import cn.wenchang.brain.search.SearchProviderException;
import cn.wenchang.brain.search.SearchProviderHealth;
import cn.wenchang.brain.search.SogouSearchProvider;
import cn.wenchang.brain.search.ChineseSearchQueryRewriter;
import cn.wenchang.brain.search.SearchResultRanker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/** Native Tool 外观；实际搜索、健康状态和错误分类由可替换 SearchProvider 负责。 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private final SearchProvider provider;
    private final ChineseSearchQueryRewriter queryRewriter;
    private final SearchResultRanker resultRanker;

    @Autowired
    public WebSearchTool(SearchProvider provider, ChineseSearchQueryRewriter queryRewriter,
                         SearchResultRanker resultRanker) {
        this.provider = provider;
        this.queryRewriter = queryRewriter;
        this.resultRanker = resultRanker;
    }

    public WebSearchTool(SearchProvider provider) {
        this(provider, new ChineseSearchQueryRewriter(), new SearchResultRanker());
    }

    /** 仅供不启动 Spring 的单元测试和兼容性 Harness 使用。 */
    public WebSearchTool(WenchangProperties properties) {
        this(new SogouSearchProvider(properties));
    }

    @Tool(name = "webSearch", description = """
            搜索互联网以取得有时效性的信息。凡涉及最近、近期、最新、今天、当前、本周、本月、
            航天发射、天气、活动、开放状态、交通或政策更新时必须调用。输入应是完整、具体的中文搜索词。
            """)
    public String webSearch(@ToolParam(description = "需要联网搜索的完整查询") String query) {
        long started = System.nanoTime();
        try {
            return formatResults(searchResults(query, 6));
        } catch (SearchProviderException exception) {
            return "联网搜索不可用：provider=" + provider.id() + "; status=UNAVAILABLE; errorType="
                    + exception.errorType() + "; detail=" + safe(exception.getMessage());
        } catch (RuntimeException exception) {
            return "联网搜索不可用：provider=" + provider.id()
                    + "; status=UNAVAILABLE; errorType=" + exception.getClass().getSimpleName()
                    + "; detail=" + safe(exception.getMessage());
        } finally {
            long latency = (System.nanoTime() - started) / 1_000_000;
            log.info("[AGENT TOOL CALL]\ntool = webSearch\nprovider = {}\nquery = {}\nlatency = {} ms",
                    provider.id(), query, latency);
        }
    }

    public List<WebSearchResult> searchResults(String query, int requestedLimit) {
        var intent = queryRewriter.rewrite(query);
        int candidateLimit = Math.max(requestedLimit, Math.min(20, requestedLimit * 2));
        List<WebSearchResult> candidates = provider.search(intent.rewrittenQuery(), candidateLimit);
        List<WebSearchResult> ranked = resultRanker.rank(intent, candidates, requestedLimit);
        log.info("[SEARCH QUERY] original={} rewritten={} candidates={} ranked={}",
                query, intent.rewrittenQuery(), candidates.size(), ranked.size());
        return ranked;
    }

    public String resolveExternalUrl(String rawLink) {
        return provider.resolveExternalUrl(rawLink);
    }

    public SearchProviderHealth healthCheck() { return provider.healthCheck(); }

    public SearchProviderHealth currentHealth() { return provider.currentHealth(); }

    public String providerId() { return provider.id(); }

    /** 保留给既有 HTML 解析契约测试；生产代码只通过 SearchProvider 调用。 */
    List<WebSearchResult> parseHtml(String html, URI baseUri, int limit) {
        if (provider instanceof SogouSearchProvider sogou) return sogou.parseHtml(html, baseUri, limit);
        throw new IllegalStateException("当前 Provider 不支持搜狗 HTML 解析契约");
    }

    private String formatResults(List<WebSearchResult> results) {
        StringBuilder output = new StringBuilder("联网搜索结果：\n");
        for (int index = 0; index < results.size(); index++) {
            WebSearchResult result = results.get(index);
            output.append(index + 1).append(". ").append(result.title()).append('\n')
                    .append("   链接：").append(result.url()).append('\n')
                    .append("   摘要：").append(result.snippet()).append('\n');
            if (!result.publishedAt().isBlank()) output.append("   发布时间：").append(result.publishedAt()).append('\n');
            if (!result.sourceProvider().isBlank()) output.append("   搜索来源：").append(result.sourceProvider()).append('\n');
        }
        if (results.isEmpty()) output.append("未找到结果。\n");
        return output.toString();
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }
}
