package cn.wenchang.mcp;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class PublicResourceTools {

    private final DataAssetRepository repository;
    private final PublicResourceProperties properties;

    public PublicResourceTools(DataAssetRepository repository, PublicResourceProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Tool(name = "searchPublicServices", description = "查询文昌经过结构化整理的医院、学校、文化场馆、体育、政务、交通、科普和应急等公共服务设施。keyword、category、town 均为可选筛选条件。")
    public SearchResponse searchPublicServices(
            @ToolParam(description = "名称、地址、服务范围或描述关键词；不限定时可留空", required = false) String keyword,
            @ToolParam(description = "公共服务类别，例如医疗、教育、文化、体育、政务、交通、科普、应急；不限定时可留空", required = false) String category,
            @ToolParam(description = "文昌乡镇或行政单元名称；不限定时可留空", required = false) String town) {
        DataAssetRepository.Snapshot snapshot = repository.snapshot();
        List<Map<String, Object>> results = snapshot.publicServices().records().stream()
                .filter(node -> matches(node, keyword, "id", "name", "address", "description", "serviceScope", "service_scope"))
                .filter(node -> matchesCategory(node, category))
                .filter(node -> matchesTown(node, town))
                .sorted(scoreComparator(keyword, category, town))
                .limit(properties.getMaxResults())
                .map(this::publicServiceView)
                .toList();
        return response("searchPublicServices", results, snapshot.publicServices(),
                dynamicNotice("公共服务设施的开放时间、接诊安排、办事条件和交通运行状态可能变化。"));
    }

    @Tool(name = "searchTownshipProfile", description = "按乡镇名称查询文昌行政单元画像，返回基本信息、公共资源、特色产业、文化生态、重要设施和可追溯来源。")
    public SearchResponse searchTownshipProfile(
            @ToolParam(description = "文昌乡镇或行政单元名称，例如龙楼镇、文城镇", required = true) String town) {
        String query = normalizeTown(town);
        if (query.isBlank()) {
            return new SearchResponse("searchTownshipProfile", 0, List.of(), "INVALID_QUERY",
                    "town 不能为空。", Instant.now().toString(), Map.of());
        }
        DataAssetRepository.Snapshot snapshot = repository.snapshot();
        List<Map<String, Object>> profiles = snapshot.townships().records().stream()
                .filter(node -> matchesTown(node, query) || matches(node, query, "name", "town", "displayName", "display_name"))
                .limit(properties.getMaxResults())
                .map(node -> townshipView(node, snapshot, query))
                .toList();
        return response("searchTownshipProfile", profiles, snapshot.townships(),
                profiles.isEmpty() ? "未找到对应乡镇的结构化画像。" : "统计、公共服务和开放状态请以最新官方发布为准。");
    }

    @Tool(name = "searchStudyTourPlaces", description = "按主题、乡镇和年龄段查询文昌研学地点，返回坐标、学习主题、学习要点、访问约束及来源。")
    public SearchResponse searchStudyTourPlaces(
            @ToolParam(description = "研学主题，例如航天、生态、历史、文化、农业；不限定时可留空", required = false) String theme,
            @ToolParam(description = "文昌乡镇或行政单元名称；不限定时可留空", required = false) String town,
            @ToolParam(description = "学习者年龄段，例如小学、初中、高中；不限定时可留空", required = false) String ageGroup) {
        DataAssetRepository.Snapshot snapshot = repository.snapshot();
        List<Map<String, Object>> results = snapshot.places().records().stream()
                .filter(node -> matches(node, theme, "category", "themes", "summary", "learningPoints", "learning_points"))
                .filter(node -> matchesTown(node, town))
                .filter(node -> matchesAgeGroup(node, ageGroup))
                .sorted(scoreComparator(theme, town, ageGroup))
                .limit(properties.getMaxResults())
                .map(this::studyTourPlaceView)
                .toList();
        return response("searchStudyTourPlaces", results, snapshot.places(),
                dynamicNotice("研学地点的预约、开放、天气、交通和安全管制必须在出发前动态核验。"));
    }

    private SearchResponse response(String tool, List<Map<String, Object>> results,
            DataAssetRepository.Asset asset, String notice) {
        return new SearchResponse(tool, results.size(), results, asset.status(), notice,
                Instant.now().toString(), Map.of("file", asset.path().toString(), "recordCount", asset.records().size()));
    }

    private String dynamicNotice(String text) {
        return text + " MCP 返回的是知识资产快照，不代表主管单位的实时承诺。";
    }

    private Map<String, Object> publicServiceView(JsonNode node) {
        return project(node,
                aliases("id"), aliases("sourceId", "source_id"), aliases("name"), aliases("category"),
                aliases("town", "district", "administrativeUnit"),
                aliases("address", "location"), aliases("latitude"), aliases("longitude"), aliases("description", "summary"),
                aliases("serviceScope", "service_scope"), aliases("sourceOrganization", "source_organization", "organization"),
                aliases("sourceUrl", "source_url", "url"), aliases("sourceLevel", "source_level"),
                aliases("retrievedAt", "retrieved_at"));
    }

    private Map<String, Object> studyTourPlaceView(JsonNode node) {
        return project(node,
                aliases("id"), aliases("sourceId", "source_id"), aliases("name"), aliases("category"),
                aliases("town", "district"),
                aliases("summary", "description"), aliases("latitude"), aliases("longitude"), aliases("themes"),
                aliases("suitableAge", "suitable_age", "ageGroup"), aliases("learningPoints", "learning_points"),
                aliases("accessType", "access_type"), aliases("sourceOrganization", "source_organization", "source"),
                aliases("sourceUrl", "source_url"), aliases("sourceLevel", "source_level"),
                aliases("retrievedAt", "retrieved_at", "verifiedAt"));
    }

    private Map<String, Object> townshipView(JsonNode node, DataAssetRepository.Snapshot snapshot, String town) {
        Map<String, Object> result = new LinkedHashMap<>();
        node.properties().forEach(field -> result.put(field.getKey(), jsonValue(field.getValue())));
        result.putIfAbsent("town", text(node, "town", "name", "displayName", "display_name"));
        List<Map<String, Object>> services = snapshot.publicServices().records().stream()
                .filter(service -> matchesTown(service, town)).limit(properties.getMaxResults())
                .map(this::publicServiceView).toList();
        List<Map<String, Object>> places = snapshot.places().records().stream()
                .filter(place -> matchesTown(place, town)).limit(properties.getMaxResults())
                .map(this::studyTourPlaceView).toList();
        result.put("publicResources", services);
        result.put("studyTourPlaces", places);
        return result;
    }

    @SafeVarargs
    private Map<String, Object> project(JsonNode node, Map.Entry<String, List<String>>... fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> field : fields) {
            JsonNode value = first(node, field.getValue());
            if (value != null && !value.isNull() && !(value.isTextual() && value.asText().isBlank())) {
                result.put(field.getKey(), jsonValue(value));
            }
        }
        return result;
    }

    private Map.Entry<String, List<String>> aliases(String canonical, String... alternatives) {
        List<String> values = new ArrayList<>();
        values.add(canonical);
        values.addAll(List.of(alternatives));
        return Map.entry(canonical, values);
    }

    private boolean matchesTown(JsonNode node, String value) {
        if (isBlank(value)) return true;
        String wanted = normalizeTown(value);
        return values(node, "town", "district", "administrativeUnit", "administrative_unit", "name").stream()
                .map(this::normalizeTown).filter(candidate -> !candidate.isBlank())
                .anyMatch(candidate -> candidate.contains(wanted) || wanted.contains(candidate));
    }

    private boolean matchesCategory(JsonNode node, String category) {
        if (isBlank(category)) return true;
        String wanted = categoryKey(category);
        return values(node, "category", "categoryName", "category_name").stream()
                .map(this::categoryKey)
                .anyMatch(actual -> actual.equals(wanted) || actual.contains(wanted) || wanted.contains(actual));
    }

    private String categoryKey(String value) {
        String normalized = normalize(value).replace("_", "").replace("-", "");
        if (containsAny(normalized, "medical", "医疗", "医院", "卫生", "急救")) return "medical";
        if (containsAny(normalized, "education", "教育", "学校", "校园")) return "education";
        if (containsAny(normalized, "culture", "文化", "图书馆", "博物馆", "文化馆")) return "culture";
        if (containsAny(normalized, "sports", "体育", "健身", "运动")) return "sports";
        if (containsAny(normalized, "government", "政务", "政府", "办事")) return "government";
        if (containsAny(normalized, "transport", "交通", "车站", "港口", "码头")) return "transport";
        if (containsAny(normalized, "science", "科普", "科研", "科技")) return "science";
        if (containsAny(normalized, "emergency", "应急", "消防", "避难", "公共安全")) return "emergency";
        if (containsAny(normalized, "publicvenue", "公共场馆", "公共场所", "场馆")) return "publicvenue";
        return normalized;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private boolean matchesAgeGroup(JsonNode node, String ageGroup) {
        if (isBlank(ageGroup)) return true;
        String suitable = text(node, "suitableAge", "suitable_age", "ageGroup", "age_group");
        if (suitable.isBlank()) return false;
        String wanted = normalize(ageGroup);
        String allowed = normalize(suitable);
        if (allowed.contains(wanted) || wanted.contains(allowed)) return true;
        int requestedRank = ageRank(wanted);
        int minimumRank = ageRank(allowed);
        return requestedRank >= 0 && minimumRank >= 0 && allowed.contains("及以上") && requestedRank >= minimumRank;
    }

    private int ageRank(String value) {
        if (value.contains("幼儿") || value.contains("学前")) return 0;
        if (value.contains("小学")) return 1;
        if (value.contains("初中")) return 2;
        if (value.contains("高中") || value.contains("中职")) return 3;
        if (value.contains("大学") || value.contains("成人")) return 4;
        return -1;
    }

    private boolean matches(JsonNode node, String value, String... fields) {
        if (isBlank(value)) return true;
        Set<String> tokens = tokens(value);
        String haystack = String.join(" ", values(node, fields)).toLowerCase(Locale.ROOT);
        return tokens.stream().allMatch(haystack::contains);
    }

    private Comparator<JsonNode> scoreComparator(String... queries) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String query : queries) tokens.addAll(tokens(query));
        return Comparator.<JsonNode>comparingInt(node -> score(node, tokens)).reversed()
                .thenComparing(node -> text(node, "name", "town", "id"));
    }

    private int score(JsonNode node, Set<String> tokens) {
        String name = text(node, "name", "town", "displayName").toLowerCase(Locale.ROOT);
        String all = node.toString().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            if (name.contains(token)) score += 4;
            if (all.contains(token)) score += 1;
        }
        return score;
    }

    private Set<String> tokens(String value) {
        if (isBlank(value)) return Set.of();
        String normalized = normalize(value);
        Set<String> result = new LinkedHashSet<>();
        for (String token : normalized.split("[\\s,，、/;；]+")) if (!token.isBlank()) result.add(token);
        return result;
    }

    private List<String> values(JsonNode node, String... fields) {
        List<String> values = new ArrayList<>();
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray()) value.forEach(item -> values.add(item.asText("")));
            else if (!value.isMissingNode() && !value.isNull()) values.add(value.asText(""));
        }
        return values;
    }

    private String text(JsonNode node, String... fields) {
        return values(node, fields).stream().filter(value -> !value.isBlank()).findFirst().orElse("");
    }

    private JsonNode first(JsonNode node, List<String> fields) {
        return fields.stream().map(node::path).filter(value -> !value.isMissingNode()).findFirst().orElse(null);
    }

    private Object jsonValue(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isIntegralNumber()) return node.asLong();
        if (node.isFloatingPointNumber()) return node.asDouble();
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(jsonValue(item)));
            return values;
        }
        if (node.isObject()) {
            Map<String, Object> values = new LinkedHashMap<>();
            node.properties().forEach(field -> values.put(field.getKey(), jsonValue(field.getValue())));
            return values;
        }
        return null;
    }

    private String normalizeTown(String value) {
        return normalize(value).replaceFirst("(乡|镇|街道|办事处)$", "");
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record SearchResponse(String tool, int count, List<Map<String, Object>> results,
            String dataStatus, String notice, String retrievedAt, Map<String, Object> dataAsset) {}
}
