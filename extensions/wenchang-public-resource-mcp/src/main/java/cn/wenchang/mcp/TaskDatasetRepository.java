package cn.wenchang.mcp;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Repository;

@Repository
public class TaskDatasetRepository {

    private static final Set<String> DATASET_TYPES = Set.of("places", "policies", "publicServices", "sources");

    private final DataAssetRepository dataRepository;
    private final ArtifactProperties properties;
    private final ObjectMapper objectMapper;

    public TaskDatasetRepository(DataAssetRepository dataRepository, ArtifactProperties properties) {
        this.dataRepository = dataRepository;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public ExportDataset export(String datasetType, List<String> requestedFields, Map<String, Object> filters) {
        String type = validateDatasetType(datasetType);
        List<Map<String, String>> all = switch (type) {
            case "places" -> nodes(dataRepository.snapshot().places().records());
            case "publicServices" -> nodes(dataRepository.snapshot().publicServices().records());
            case "policies" -> nodes(readJsonArray(dataRoot().resolve(properties.getPoliciesFile()), "policies"));
            case "sources" -> readSources();
            default -> throw new IllegalArgumentException("Unsupported datasetType: " + type);
        };
        List<String> fields = normalizeFields(requestedFields, defaultFields(type));
        List<Map<String, String>> rows = all.stream().filter(row -> matchesFilters(row, filters))
                .limit(properties.getMaxExportRows()).map(row -> project(row, fields)).toList();
        return new ExportDataset(type, fields, rows);
    }

    public List<Map<String, String>> studyTourPlaces(String ageGroup, List<String> themes,
                                                      List<String> preferences) {
        Set<String> terms = terms(themes, preferences);
        return nodes(dataRepository.snapshot().places().records()).stream()
                .filter(row -> ageMatches(row.get("suitableAge"), ageGroup))
                .filter(row -> terms.isEmpty() || terms.stream().anyMatch(term -> searchable(row).contains(term)))
                .sorted(Comparator.comparingInt((Map<String, String> row) -> relevance(row, terms)).reversed()
                        .thenComparing(row -> row.getOrDefault("name", "")))
                .limit(8).toList();
    }

    public List<Map<String, String>> policies(String topic, String timeRange, String focus) {
        Set<String> terms = terms(List.of(topic == null ? "" : topic, focus == null ? "" : focus), List.of());
        List<Map<String, String>> policies = nodes(readJsonArray(
                dataRoot().resolve(properties.getPoliciesFile()), "policies"));
        return policies.stream()
                .filter(row -> terms.isEmpty() || terms.stream().anyMatch(term -> searchable(row).contains(term)))
                .filter(row -> inTimeRange(row.get("publishedAt"), timeRange))
                .sorted(Comparator.comparing((Map<String, String> row) -> row.getOrDefault("publishedAt", ""))
                        .reversed().thenComparing(row -> row.getOrDefault("title", "")))
                .limit(20).toList();
    }

    public List<Map<String, String>> sources() { return readSources(); }

    private List<Map<String, String>> nodes(List<JsonNode> records) {
        return records.stream().map(this::flatten).toList();
    }

    private Map<String, String> flatten(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        node.properties().forEach(field -> result.put(field.getKey(), text(field.getValue())));
        return result;
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> values.add(text(item)));
            return String.join("、", values);
        }
        if (node.isObject()) return node.toString();
        return node.asText("");
    }

    private List<JsonNode> readJsonArray(Path path, String key) {
        if (!Files.isRegularFile(path)) return List.of();
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            JsonNode array = root.isArray() ? root : root.path(key);
            if (!array.isArray()) return List.of();
            List<JsonNode> result = new ArrayList<>();
            array.forEach(result::add);
            return List.copyOf(result);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot read dataset: " + path.getFileName(), exception);
        }
    }

    private List<Map<String, String>> readSources() {
        Path path = resolveSourcesPath();
        if (!Files.isRegularFile(path)) return List.of();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            return parser.stream().map(record -> new LinkedHashMap<>(record.toMap())).map(Map::copyOf).toList();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot read sources index", exception);
        }
    }

    private Path resolveSourcesPath() {
        if (properties.getSourcesIndexFile() != null && !properties.getSourcesIndexFile().toString().isBlank()) {
            return properties.getSourcesIndexFile().toAbsolutePath().normalize();
        }
        Path projectCandidate = dataRoot().getParent() == null ? Path.of("knowledge", "SOURCES_INDEX.csv")
                : dataRoot().getParent().resolve("knowledge").resolve("SOURCES_INDEX.csv");
        if (Files.isRegularFile(projectCandidate)) return projectCandidate.toAbsolutePath().normalize();
        return Path.of("knowledge", "SOURCES_INDEX.csv").toAbsolutePath().normalize();
    }

    private Path dataRoot() { return dataRepository.snapshot().dataRoot(); }

    private boolean matchesFilters(Map<String, String> row, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return true;
        return filters.entrySet().stream().allMatch(filter -> {
            String actual = row.getOrDefault(filter.getKey(), "");
            String expected = filter.getValue() == null ? "" : String.valueOf(filter.getValue());
            return expected.isBlank() || normalize(actual).contains(normalize(expected));
        });
    }

    private Map<String, String> project(Map<String, String> source, List<String> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        fields.forEach(field -> result.put(field, source.getOrDefault(field, "")));
        return result;
    }

    private List<String> normalizeFields(List<String> fields, List<String> defaults) {
        List<String> selected = fields == null ? List.of() : fields.stream().filter(value -> value != null)
                .map(String::trim).filter(value -> value.matches("[A-Za-z][A-Za-z0-9_]{0,63}"))
                .distinct().limit(50).toList();
        return selected.isEmpty() ? defaults : selected;
    }

    private List<String> defaultFields(String type) {
        return switch (type) {
            case "places" -> List.of("name", "category", "town", "summary", "latitude", "longitude",
                    "suitableAge", "sourceOrganization", "sourceUrl");
            case "policies" -> List.of("title", "organization", "publishedAt", "status", "category",
                    "summary", "sourceUrl");
            case "publicServices" -> List.of("name", "category", "town", "address", "serviceScope",
                    "sourceOrganization", "sourceUrl");
            case "sources" -> List.of("source_id", "title", "organization", "url", "source_level",
                    "category", "published_at", "status");
            default -> List.of();
        };
    }

    private String validateDatasetType(String value) {
        String candidate = value == null ? "" : value.trim();
        if (!DATASET_TYPES.contains(candidate)) throw new IllegalArgumentException("Unsupported datasetType: " + value);
        return candidate;
    }

    private Set<String> terms(List<String> first, List<String> second) {
        Set<String> values = new LinkedHashSet<>();
        for (String item : concat(first, second)) {
            if (item == null) continue;
            String normalized = normalize(item);
            for (String token : normalized.split("[\\s,，、/;；]+")) if (!token.isBlank()) values.add(token);
            for (String keyword : List.of("文昌", "商业航天", "航天", "生态", "产业", "人才", "城市",
                    "发展", "研学", "政策", "公共服务")) {
                if (normalized.contains(keyword)) values.add(keyword);
            }
        }
        return values;
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> values = new ArrayList<>();
        if (first != null) values.addAll(first);
        if (second != null) values.addAll(second);
        return values;
    }

    private String searchable(Map<String, String> row) {
        return normalize(String.join(" ", row.values()));
    }

    private int relevance(Map<String, String> row, Set<String> terms) {
        String name = normalize(row.getOrDefault("name", ""));
        String all = searchable(row);
        int score = 0;
        for (String term : terms) {
            if (name.contains(term)) score += 4;
            if (all.contains(term)) score++;
        }
        return score;
    }

    private boolean ageMatches(String suitable, String wanted) {
        if (wanted == null || wanted.isBlank()) return true;
        String actual = normalize(suitable);
        String requested = normalize(wanted);
        if (actual.isBlank()) return false;
        if (actual.contains(requested) || requested.contains(actual)) return true;
        int actualRank = ageRank(actual);
        int requestedRank = ageRank(requested);
        return actualRank >= 0 && requestedRank >= 0 && actual.contains("及以上") && requestedRank >= actualRank;
    }

    private int ageRank(String value) {
        if (value.contains("幼儿") || value.contains("学前")) return 0;
        if (value.contains("小学") || value.matches(".*[一二三四五六]年级.*")) return 1;
        if (value.contains("初中") || value.contains("初一") || value.contains("初二") || value.contains("初三")
                || value.contains("七年级") || value.contains("八年级") || value.contains("九年级")) return 2;
        if (value.contains("高中") || value.contains("中职")) return 3;
        if (value.contains("大学") || value.contains("成人")) return 4;
        return -1;
    }

    private boolean inTimeRange(String publishedAt, String timeRange) {
        if (timeRange == null || timeRange.isBlank() || publishedAt == null || publishedAt.isBlank()) return true;
        List<Integer> years = java.util.regex.Pattern.compile("20\\d{2}").matcher(timeRange).results()
                .map(result -> Integer.parseInt(result.group())).toList();
        if (years.isEmpty()) return true;
        int year;
        try { year = Integer.parseInt(publishedAt.substring(0, 4)); }
        catch (RuntimeException exception) { return false; }
        int minimum = years.stream().min(Integer::compareTo).orElse(year);
        int maximum = years.stream().max(Integer::compareTo).orElse(year);
        return year >= minimum && year <= maximum;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public record ExportDataset(String type, List<String> fields, List<Map<String, String>> rows) { }
}
