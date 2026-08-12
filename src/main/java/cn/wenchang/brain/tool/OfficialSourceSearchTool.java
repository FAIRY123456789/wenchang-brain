package cn.wenchang.brain.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 检索文昌、海南与国家相关官方机构资料。
 *
 * <p>搜索引擎只负责发现候选结果；最终 URL 必须严格匹配 data/official-source-registry.json
 * 中已经人工核验的域名。不能解析或不在白名单内的结果一律不返回。</p>
 */
@Component
public class OfficialSourceSearchTool {

    private static final Logger log = LoggerFactory.getLogger(OfficialSourceSearchTool.class);
    private static final int MAX_SOURCES_PER_QUERY = 6;
    private static final int MAX_RESULTS = 6;

    private final OfficialSourceRegistry registry;
    private final WebSearchTool webSearchTool;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public OfficialSourceSearchTool(OfficialSourceRegistry registry, WebSearchTool webSearchTool) {
        this.registry = registry;
        this.webSearchTool = webSearchTool;
    }

    @Tool(name = "officialSourceSearch", description = """
            优先检索经过核验的文昌市、海南省和国家官方机构网站。用户询问政策、行政数据、统计数据、
            航天任务、生态保护、教育、政府项目或明确要求官方资料时使用。只返回官方域名白名单内的结果，
            每条结果包含标题、URL、摘要、来源机构和检索时间。
            """)
    public String officialSourceSearch(
            @ToolParam(description = "需要查找官方依据的完整中文问题") String query) {
        String normalized = query == null ? "" : query.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) return json(new OfficialSearchResponse(List.of(), "查询不能为空"));

        List<OfficialSourceRegistry.OfficialSource> sources = registry.candidates(normalized, MAX_SOURCES_PER_QUERY);
        if (sources.isEmpty()) {
            return json(new OfficialSearchResponse(List.of(),
                    "官方来源注册表尚未提供可用来源，未返回普通网页作为替代。"));
        }

        Map<String, OfficialSearchResult> unique = new LinkedHashMap<>();
        for (OfficialSourceRegistry.OfficialSource source : sources) {
            if (unique.size() >= MAX_RESULTS) break;
            try {
                List<WebSearchResult> candidates = webSearchTool.searchResults(
                        normalized + " site:" + source.domain(), 4);
                for (WebSearchResult candidate : candidates) {
                    String resolvedUrl = webSearchTool.resolveExternalUrl(candidate.url());
                    if (!isAllowed(resolvedUrl, source)) continue;
                    unique.putIfAbsent(resolvedUrl, new OfficialSearchResult(candidate.title(), resolvedUrl,
                            candidate.snippet(), source.name(), Instant.now()));
                    if (unique.size() >= MAX_RESULTS) break;
                }
            } catch (Exception exception) {
                log.warn("Official search failed for domain={} error={}", source.domain(),
                        exception.getClass().getSimpleName());
            }
        }
        String message = unique.isEmpty()
                ? "没有找到可验证为官方域名的结果，未返回普通网页作为替代。" : "";
        return json(new OfficialSearchResponse(List.copyOf(unique.values()), message));
    }

    boolean isAllowed(String url, OfficialSourceRegistry.OfficialSource source) {
        if (url == null || url.isBlank() || source == null || source.domain().isBlank()) return false;
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String domain = source.domain().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            return host.equals(domain) || (source.includeSubdomains() && host.endsWith("." + domain));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String json(OfficialSearchResponse response) {
        try { return objectMapper.writeValueAsString(response); }
        catch (JsonProcessingException exception) { return "{\"results\":[],\"message\":\"结果序列化失败\"}"; }
    }

    public record OfficialSearchResult(String title, String url, String snippet,
                                       String sourceOrganization, Instant retrievedAt) { }
    public record OfficialSearchResponse(List<OfficialSearchResult> results, String message) { }
}
