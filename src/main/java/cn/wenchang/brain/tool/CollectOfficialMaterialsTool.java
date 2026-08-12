package cn.wenchang.brain.tool;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.search.SearchProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 采集公开资料并将临时 Research Dataset 持久化到 conversation 隔离目录。 */
@Component
public class CollectOfficialMaterialsTool {

    private static final Pattern DATE = Pattern.compile("(20\\d{2})[-年/.](\\d{1,2})[-月/.](\\d{1,2})日?");
    private static final String SCHEMA = """
            {"type":"object","properties":{
              "topic":{"type":"string","description":"需要采集公开资料的明确主题"},
              "categories":{"type":"array","items":{"type":"string"},"description":"资料分类"},
              "maxSources":{"type":"integer","minimum":1,"maximum":20,"description":"最大来源数"}
            },"required":["topic"]}
            """;

    private final OfficialSourceSearchTool officialSearch;
    private final WebSearchTool webSearch;
    private final Path researchRoot;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public CollectOfficialMaterialsTool(OfficialSourceSearchTool officialSearch, WebSearchTool webSearch,
                                        WenchangProperties properties) {
        this.officialSearch = officialSearch;
        this.webSearch = webSearch;
        this.researchRoot = Path.of(properties.getResearchDir()).toAbsolutePath().normalize();
    }

    public ToolCallback callback() {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("collectOfficialMaterials")
                        .description("围绕明确主题检索官方来源和公开网页，打开候选原始来源并提取标题、机构、日期、URL与摘要，保存临时 Research Dataset。")
                        .inputSchema(SCHEMA).build();
            }

            @Override public String call(String input) { return execute(input, null); }

            @Override public String call(String input, ToolContext context) { return execute(input, context); }
        };
    }

    private String execute(String input, ToolContext context) {
        try {
            JsonNode request = mapper.readTree(input == null ? "{}" : input);
            String topic = request.path("topic").asText("").replaceAll("\\s+", " ").trim();
            if (topic.isBlank()) throw new IllegalArgumentException("topic 不能为空");
            int maxSources = Math.max(1, Math.min(20, request.path("maxSources").asInt(8)));
            List<String> categories = new ArrayList<>();
            request.path("categories").forEach(item -> {
                if (!item.asText("").isBlank()) categories.add(item.asText().trim());
            });
            String conversationId = conversationId(context);
            List<Material> materials = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            Set<String> urls = new LinkedHashSet<>();

            collectOfficial(topic, maxSources, categories, materials, urls, errors);
            if (materials.size() < maxSources) {
                collectWeb(topic, maxSources, categories, materials, urls, errors);
            }

            String datasetId = "research-" + UUID.randomUUID();
            ResearchDataset dataset = new ResearchDataset(datasetId, conversationId, topic,
                    List.copyOf(categories), Instant.now(), List.copyOf(materials), List.copyOf(errors));
            Path directory = researchRoot.resolve(safeSegment(conversationId)).normalize();
            if (!directory.startsWith(researchRoot)) throw new IllegalStateException("Invalid conversationId");
            Files.createDirectories(directory);
            Path file = directory.resolve(datasetId + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), dataset);
            return mapper.writeValueAsString(Map.of(
                    "datasetId", datasetId,
                    "conversationId", conversationId,
                    "file", researchRoot.relativize(file).toString().replace('\\', '/'),
                    "sourceCount", materials.size(),
                    "sources", materials,
                    "errors", errors));
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("Research Dataset 生成失败", exception);
        }
    }

    private void collectOfficial(String topic, int maxSources, List<String> categories,
                                 List<Material> result, Set<String> urls, List<String> errors) {
        try {
            JsonNode response = mapper.readTree(officialSearch.officialSourceSearch(topic));
            for (JsonNode item : response.path("results")) {
                if (result.size() >= maxSources) break;
                addMaterial(item.path("title").asText(), item.path("sourceOrganization").asText(),
                        date(item.path("snippet").asText()), item.path("url").asText(),
                        item.path("snippet").asText(), "OFFICIAL", categories, result, urls);
            }
            String message = response.path("message").asText("");
            if (!message.isBlank()) errors.add("officialSourceSearch: " + message);
        } catch (Exception exception) {
            errors.add("officialSourceSearch: " + safe(exception.getMessage()));
        }
    }

    private void collectWeb(String topic, int maxSources, List<String> categories,
                            List<Material> result, Set<String> urls, List<String> errors) {
        try {
            for (WebSearchResult item : webSearch.searchResults(topic, maxSources)) {
                if (result.size() >= maxSources) break;
                String url = webSearch.resolveExternalUrl(item.url());
                addMaterial(item.title(), organization(url), date(item.snippet()), url,
                        item.snippet(), "WEB", categories, result, urls);
            }
        } catch (SearchProviderException exception) {
            errors.add("webSearch[" + exception.errorType() + "]: " + safe(exception.getMessage()));
        } catch (Exception exception) {
            errors.add("webSearch: " + safe(exception.getMessage()));
        }
    }

    private void addMaterial(String title, String organization, String date, String url, String searchSummary,
                             String sourceType, List<String> categories, List<Material> result, Set<String> urls) {
        if (url == null || url.isBlank() || !urls.add(url)) return;
        Page page = fetchPage(url);
        String resolvedTitle = title == null || title.isBlank() ? page.title() : title;
        String summary = page.summary().isBlank() ? searchSummary : page.summary();
        result.add(new Material(resolvedTitle, organization, date, url, summary,
                sourceType, List.copyOf(categories), page.opened()));
    }

    private Page fetchPage(String url) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                return new Page("", "", false);
            }
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 WenchangBrain/1.4");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return new Page("", "", false);
            try (InputStream input = connection.getInputStream()) {
                String html = new String(input.readNBytes(600_000), StandardCharsets.UTF_8);
                Matcher title = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
                String pageTitle = title.find() ? clean(title.group(1)) : "";
                String text = clean(html);
                return new Page(pageTitle, text.length() > 360 ? text.substring(0, 360) + "…" : text, true);
            }
        } catch (Exception ignored) {
            return new Page("", "", false);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String conversationId(ToolContext context) {
        if (context != null && context.getContext() != null) {
            Object value = context.getContext().get("wenchang.conversationId");
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "standalone-" + UUID.randomUUID();
    }

    private String safeSegment(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "standalone" : safe.substring(0, Math.min(120, safe.length()));
    }

    private String organization(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception ignored) { return ""; }
    }

    private String date(String text) {
        Matcher matcher = DATE.matcher(text == null ? "" : text);
        if (!matcher.find()) return "";
        return "%s-%02d-%02d".formatted(matcher.group(1), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    private String clean(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }

    public record Material(String title, String organization, String date, String url, String summary,
                           String sourceType, List<String> categories, boolean originalOpened) { }
    public record ResearchDataset(String id, String conversationId, String topic, List<String> categories,
                                  Instant createdAt, List<Material> sources, List<String> errors) { }
    private record Page(String title, String summary, boolean opened) { }
}
