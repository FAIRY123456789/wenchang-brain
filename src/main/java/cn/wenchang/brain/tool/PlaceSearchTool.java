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
import java.util.List;
import java.util.Locale;

/** 查询经过来源和坐标校验的文昌地点结构化资产。 */
@Component
public class PlaceSearchTool {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public PlaceSearchTool(@Value("${wenchang.places-file:data/wenchang-places.json}") String file) {
        this.file = Path.of(file).toAbsolutePath().normalize();
    }

    @Tool(name = "placeSearch", description = """
            查询文昌经过核验的地点。可按名称、类型、乡镇、主题和研学年龄筛选，返回真实坐标、
            学习目标与来源。不得补写数据中不存在的开放时间、地址或坐标。
            """)
    public String placeSearch(
            @ToolParam(description = "地点名称或关键词，可为空") String keyword,
            @ToolParam(description = "地点类型，可为空") String category,
            @ToolParam(description = "所属乡镇，可为空") String town,
            @ToolParam(description = "航天、生态、文化等主题，可为空") String theme,
            @ToolParam(description = "研学年龄或学段，可为空") String ageGroup) {
        try {
            return mapper.writeValueAsString(new PlaceSearchResponse(search(keyword, category, town, theme, ageGroup, 12)));
        } catch (Exception exception) {
            return "{\"places\":[],\"message\":\"地点数据暂不可用\"}";
        }
    }

    public List<PlaceItem> search(String keyword, String category, String town, String theme,
                                  String ageGroup, int limit) {
        if (!Files.isRegularFile(file)) return List.of();
        try {
            JsonNode root = mapper.readTree(file.toFile());
            JsonNode places = root.isArray() ? root : root.path("places");
            if (!places.isArray()) return List.of();
            List<PlaceItem> result = new ArrayList<>();
            for (JsonNode node : places) {
                PlaceItem item = item(node);
                String haystack = (item.name() + " " + item.category() + " " + item.town() + " "
                        + item.summary() + " " + String.join(" ", item.themes()) + " "
                        + String.join(" ", item.ageGroups())).toLowerCase(Locale.ROOT);
                if (!matches(haystack, keyword) || !matches(item.category(), category)
                        || !matches(item.town(), town) || !matches(haystack, theme)
                        || !matches(haystack, ageGroup)) continue;
                if (item.latitude() == null || item.longitude() == null) continue;
                result.add(item);
                if (result.size() >= Math.max(1, limit)) break;
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private PlaceItem item(JsonNode node) {
        return new PlaceItem(text(node, "id"), text(node, "sourceId"), text(node, "name"), text(node, "category"),
                firstText(node, "town", "district"), firstText(node, "address", "location"),
                number(node, "latitude"), number(node, "longitude"), text(node, "summary"),
                strings(node, "themes"), strings(node, "learningPoints"),
                stringsOrSingle(node, "ageGroups", "suitableAge"),
                firstText(node, "sourceOrganization", "source"),
                firstText(node, "sourceUrl", "source_url"), firstText(node, "sourceLevel", "source_level"));
    }

    private boolean matches(String value, String expected) {
        return expected == null || expected.isBlank()
                || (value != null && value.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT)));
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isValueNode() ? value.asText("").trim() : "";
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) { String value = text(node, key); if (!value.isBlank()) return value; }
        return "";
    }

    private Double number(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isNumber() ? value.asDouble() : null;
    }

    private List<String> strings(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        value.forEach(item -> { if (item.isValueNode() && !item.asText().isBlank()) values.add(item.asText().trim()); });
        return List.copyOf(values);
    }

    private List<String> stringsOrSingle(JsonNode node, String arrayKey, String singleKey) {
        List<String> values = strings(node, arrayKey);
        if (!values.isEmpty()) return values;
        String single = text(node, singleKey);
        return single.isBlank() ? List.of() : List.of(single);
    }

    public record PlaceItem(String id, String sourceId, String name, String category, String town, String location,
                            Double latitude, Double longitude, String summary, List<String> themes,
                            List<String> learningPoints, List<String> ageGroups,
                            String sourceOrganization, String sourceUrl, String sourceLevel) { }
    public record PlaceSearchResponse(List<PlaceItem> places) { }
}
