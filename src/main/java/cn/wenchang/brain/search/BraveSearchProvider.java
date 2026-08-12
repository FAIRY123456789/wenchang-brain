package cn.wenchang.brain.search;

import cn.wenchang.brain.tool.WebSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Brave Search's official independent-index JSON API adapter. */
public final class BraveSearchProvider extends AbstractStandardApiSearchProvider {

    private final String endpoint;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public BraveSearchProvider(String endpoint, String apiKey, int timeoutSeconds) {
        super("brave", timeoutSeconds);
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    @Override
    protected List<WebSearchResult> executeSearch(String query, int limit) {
        try {
            String separator = endpoint.contains("?") ? "&" : "?";
            String url = endpoint + separator + "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&count=" + limit + "&search_lang=zh-hans&country=cn&text_decorations=false"
                    + (looksLikeNews(query) ? "&freshness=pm" : "");
            String json = executeJson("GET", url,
                    Map.of("X-Subscription-Token", apiKey), null);
            return parseResponse(json, limit);
        } catch (SearchProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SearchProviderException("SEARCH_RESPONSE_INVALID", safe(exception.getMessage()), exception);
        }
    }

    List<WebSearchResult> parseResponse(String json, int limit) throws Exception {
        JsonNode root = mapper.readTree(json);
        List<WebSearchResult> output = new ArrayList<>();
        append(root.path("web").path("results"), output, limit);
        if (output.size() < limit) append(root.path("news").path("results"), output, limit);
        return output;
    }

    private void append(JsonNode results, List<WebSearchResult> output, int limit) {
        if (!results.isArray()) return;
        for (JsonNode item : results) {
            String title = item.path("title").asText("").trim();
            String url = item.path("url").asText("").trim();
            String description = item.path("description").asText("").trim();
            String publishedAt = item.path("page_age").asText(item.path("age").asText("")).trim();
            if (!title.isBlank() && !url.isBlank()) {
                output.add(new WebSearchResult(title, url, description, publishedAt, id()));
            }
            if (output.size() >= limit) return;
        }
    }

    private boolean looksLikeNews(String query) {
        String value = query.toLowerCase(java.util.Locale.ROOT);
        return value.contains("新闻") || value.contains("最新") || value.contains("近期")
                || value.contains("today") || value.contains("news");
    }
}
