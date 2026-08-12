package cn.wenchang.brain.search;

import cn.wenchang.brain.tool.WebSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tavily's official JSON Search API adapter. */
public final class TavilySearchProvider extends AbstractStandardApiSearchProvider {

    private static final Pattern SITE_FILTER = Pattern.compile("(?i)site:([a-z0-9.-]+)");

    private final String endpoint;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public TavilySearchProvider(String endpoint, String apiKey, int timeoutSeconds) {
        super("tavily", timeoutSeconds);
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    @Override
    protected List<WebSearchResult> executeSearch(String query, int limit) {
        try {
            Set<String> domains = extractDomains(query);
            String effectiveQuery = SITE_FILTER.matcher(query).replaceAll(" ").replaceAll("\\s+", " ").trim();
            ObjectNode payload = mapper.createObjectNode();
            payload.put("query", effectiveQuery);
            payload.put("search_depth", "basic");
            boolean news = looksLikeNews(effectiveQuery);
            payload.put("topic", news ? "news" : "general");
            if (news) payload.put("days", 30);
            if (!domains.isEmpty()) {
                var includeDomains = payload.putArray("include_domains");
                domains.forEach(includeDomains::add);
            }
            payload.put("max_results", limit);
            payload.put("include_answer", false);
            payload.put("include_raw_content", false);
            String json = executeJson("POST", endpoint,
                    Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json"),
                    mapper.writeValueAsString(payload));
            return parseResponse(json, limit);
        } catch (SearchProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SearchProviderException("SEARCH_RESPONSE_INVALID", safe(exception.getMessage()), exception);
        }
    }

    List<WebSearchResult> parseResponse(String json, int limit) throws Exception {
        JsonNode results = mapper.readTree(json).path("results");
        List<WebSearchResult> output = new ArrayList<>();
        if (!results.isArray()) return output;
        for (JsonNode item : results) {
            String title = item.path("title").asText("").trim();
            String url = item.path("url").asText("").trim();
            String snippet = item.path("content").asText("").trim();
            String publishedAt = item.path("published_date").asText("").trim();
            if (!title.isBlank() && !url.isBlank()) {
                output.add(new WebSearchResult(title, url, snippet, publishedAt, id()));
            }
            if (output.size() >= limit) break;
        }
        return output;
    }

    private boolean looksLikeNews(String query) {
        String value = query.toLowerCase(java.util.Locale.ROOT);
        return value.contains("新闻") || value.contains("最新") || value.contains("近期")
                || value.contains("today") || value.contains("news");
    }

    private Set<String> extractDomains(String query) {
        Set<String> domains = new LinkedHashSet<>();
        Matcher matcher = SITE_FILTER.matcher(query);
        while (matcher.find()) domains.add(matcher.group(1).toLowerCase(java.util.Locale.ROOT));
        return domains;
    }
}
