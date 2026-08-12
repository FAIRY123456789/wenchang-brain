package cn.wenchang.brain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 查询与文昌直接相关、保留官方原始链接的政策结构化资产。 */
@Component
public class PolicySearchTool {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public PolicySearchTool(@Value("${wenchang.policies-file:data/wenchang-policies.json}") String file) {
        this.file = Path.of(file).toAbsolutePath().normalize();
    }

    @Tool(name = "policySearch", description = """
            查询文昌、海南及与文昌直接相关的国家政策。返回标题、发布机构、文号、发布日期、
            生效日期、状态、分类、摘要和官方原始链接。只有数据明确记录时才判断政策状态。
            """)
    public String policySearch(
            @ToolParam(description = "政策主题或关键词") String query,
            @ToolParam(description = "政策分类，可为空") String category,
            @ToolParam(description = "CURRENT、EXPIRED、SUPERSEDED、UNKNOWN，可为空") String status) {
        try {
            return mapper.writeValueAsString(new PolicySearchResponse(search(query, category, status, 10)));
        } catch (Exception exception) {
            return "{\"policies\":[],\"message\":\"政策数据暂不可用\"}";
        }
    }

    public List<PolicyItem> search(String query, String category, String status, int limit) {
        if (!Files.isRegularFile(file)) return List.of();
        try {
            JsonNode root = mapper.readTree(file.toFile());
            JsonNode policies = root.isArray() ? root : root.path("policies");
            if (!policies.isArray()) return List.of();
            List<PolicyItem> result = new ArrayList<>();
            for (JsonNode node : policies) {
                PolicyItem item = item(node);
                String haystack = (item.title() + " " + item.organization() + " " + item.documentNumber()
                        + " " + String.join(" ", item.categories()) + " " + item.summary()).toLowerCase(Locale.ROOT);
                if (!matchesQuery(haystack, query) || !matches(String.join(" ", item.categories()), category)
                        || !matches(item.status(), status)) continue;
                result.add(item);
            }
            result.sort(Comparator.comparing(PolicyItem::publishedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            return List.copyOf(result.subList(0, Math.min(result.size(), Math.max(1, limit))));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private PolicyItem item(JsonNode node) {
        return new PolicyItem(text(node, "id"), text(node, "sourceId"), text(node, "title"), text(node, "organization"),
                text(node, "documentNumber"), text(node, "publishedAt"), text(node, "effectiveAt"),
                text(node, "expiryAt"), text(node, "status"), strings(node, "categories"),
                text(node, "summary"), text(node, "sourceUrl"), text(node, "retrievedAt"));
    }

    private boolean matches(String value, String expected) {
        return expected == null || expected.isBlank()
                || (value != null && value.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT)));
    }

    private boolean matchesQuery(String value, String query) {
        if (query == null || query.isBlank()) return true;
        String normalized = query.toLowerCase(Locale.ROOT)
                .replaceAll("[，。！？、；：,.!?;:]", " ")
                .replaceAll("(我想知道|请问|请|帮我|整理|查询|查找|最近的|最新的|近期|最近|最新|文昌市|文昌|政策|发布)", " ")
                .replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) return true;
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2 && value.contains(token)) return true;
        }
        return false;
    }

    private String text(JsonNode node, String key) { return node.path(key).asText("").trim(); }
    private List<String> strings(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        value.forEach(item -> { if (!item.asText("").isBlank()) result.add(item.asText().trim()); });
        return List.copyOf(result);
    }

    public record PolicyItem(String id, String sourceId, String title, String organization, String documentNumber,
                             String publishedAt, String effectiveAt, String expiryAt, String status,
                             List<String> categories, String summary, String sourceUrl, String retrievedAt) { }
    public record PolicySearchResponse(List<PolicyItem> policies) { }
}
