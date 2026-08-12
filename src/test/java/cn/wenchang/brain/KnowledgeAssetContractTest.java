package cn.wenchang.brain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeAssetContractTest {

    private static final List<String> REQUIRED_FRONT_MATTER = List.of(
            "title", "category", "source_id", "source_organization", "source_url", "source_level",
            "published_at", "retrieved_at", "updated_at", "tags", "index_status", "temporal_type");

    @Test
    void activeKnowledgeHasCompleteUniqueTraceableMetadata() throws Exception {
        Path root = Path.of("knowledge");
        List<Path> documents;
        try (var stream = Files.walk(root)) {
            documents = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !root.relativize(path).toString().startsWith("_"))
                    .toList();
        }
        Set<String> ids = new HashSet<>();
        Set<String> urls = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (Path document : documents) {
            Map<String, String> metadata = frontMatter(document);
            assertThat(metadata).as(document.toString()).containsKeys(REQUIRED_FRONT_MATTER.toArray(String[]::new));
            assertThat(metadata.get("index_status")).isEqualTo("active");
            assertThat(metadata.get("source_level")).isIn("P0", "P1", "P2");
            assertThat(metadata.get("source_url")).startsWith("http");
            assertThat(ids.add(metadata.get("source_id"))).as("unique source_id").isTrue();
            assertThat(urls.add(metadata.get("source_url"))).as("unique source_url").isTrue();
            categories.add(metadata.get("category"));
        }
        assertThat(documents).hasSizeGreaterThanOrEqualTo(30);
        assertThat(categories).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void sourceIndexRegistryAndPlacesStayReferentiallyConsistent() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("knowledge", "SOURCES_INDEX.csv"), StandardCharsets.UTF_8);
        List<String> header = parseCsvLine(lines.get(0));
        Set<String> sourceIds = new HashSet<>();
        Set<String> sourceUrls = new HashSet<>();
        int active = 0;
        int supporting = 0;
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> values = parseCsvLine(line);
            Map<String, String> row = new HashMap<>();
            for (int index = 0; index < header.size(); index++) row.put(header.get(index), values.get(index));
            assertThat(sourceIds.add(row.get("source_id"))).isTrue();
            assertThat(sourceUrls.add(row.get("url"))).isTrue();
            if ("active".equals(row.get("status"))) {
                active++;
                assertThat(row.get("local_file")).startsWith("knowledge/");
            } else if ("supporting".equals(row.get("status"))) {
                supporting++;
                assertThat(row.get("local_file")).isBlank();
            }
        }
        assertThat(active).isGreaterThanOrEqualTo(30);
        assertThat(supporting).isGreaterThanOrEqualTo(10);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode registry = mapper.readTree(Path.of("data", "official-source-registry.json").toFile()).path("sources");
        assertThat(registry.isArray()).isTrue();
        assertThat(registry.size()).isGreaterThanOrEqualTo(15);

        JsonNode places = mapper.readTree(Path.of("data", "wenchang-places.json").toFile()).path("places");
        assertThat(places.isArray()).isTrue();
        assertThat(places.size()).isGreaterThanOrEqualTo(12);
        Set<String> placeIds = new HashSet<>();
        for (JsonNode place : places) {
            assertThat(placeIds.add(place.path("id").asText())).isTrue();
            assertThat(sourceIds).contains(place.path("sourceId").asText());
            assertThat(place.path("sourceUrl").asText()).startsWith("http");
            assertThat(place.path("longitude").isNull()).isEqualTo(place.path("latitude").isNull());
        }
    }

    private Map<String, String> frontMatter(Path document) throws Exception {
        List<String> lines = Files.readAllLines(document, StandardCharsets.UTF_8);
        assertThat(lines.get(0)).as(document.toString()).isEqualTo("---");
        Map<String, String> result = new HashMap<>();
        for (int index = 1; index < lines.size() && !"---".equals(lines.get(index)); index++) {
            String line = lines.get(index);
            int colon = line.indexOf(':');
            if (colon > 0) result.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        return result;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else quoted = !quoted;
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else value.append(character);
        }
        values.add(value.toString());
        return values;
    }
}
