package cn.wenchang.brain.v14;

import cn.wenchang.brain.tool.PlaceSearchTool;
import cn.wenchang.brain.tool.PolicySearchTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class V14DataAndToolContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Path DATA = Path.of("data");

    @Test
    void activePlacesHaveTraceableSourcesAndNoMissingCoordinates() throws IOException {
        JsonNode places = requiredArray(DATA.resolve("wenchang-places.json"), "places");
        assertThat(places.size()).isGreaterThanOrEqualTo(16);
        assertUniqueIdsAndRequiredSourceIds(places);
        for (JsonNode item : places) {
            assertText(item, "name", "category", "town", "sourceOrganization", "sourceUrl",
                    "coordinateSource", "coordinateRetrievedAt", "coordinatePrecision");
            assertThat(item.path("latitude").isNumber()).as(item.path("name").asText()).isTrue();
            assertThat(item.path("longitude").isNumber()).as(item.path("name").asText()).isTrue();
            assertThat(item.path("latitude").asDouble()).isBetween(-90.0, 90.0);
            assertThat(item.path("longitude").asDouble()).isBetween(-180.0, 180.0);
        }

        PlaceSearchTool tool = new PlaceSearchTool(DATA.resolve("wenchang-places.json").toString());
        assertThat(tool.search("文昌航天科普中心", "", "", "", "", 5))
                .singleElement()
                .satisfies(place -> {
                    assertThat(place.sourceId()).startsWith("SRC-");
                    assertThat(place.sourceOrganization()).isNotBlank();
                    assertThat(place.sourceUrl()).startsWith("http");
                    assertThat(place.latitude()).isNotNull();
                    assertThat(place.longitude()).isNotNull();
                });
    }

    @Test
    void policiesAndPublicServicesAreStructuredAndTraceable() throws IOException {
        JsonNode policies = requiredArray(DATA.resolve("wenchang-policies.json"), "policies");
        assertThat(policies).isNotEmpty();
        assertUniqueIdsAndRequiredSourceIds(policies);
        for (JsonNode item : policies) {
            assertText(item, "title", "organization", "publishedAt", "status", "summary", "sourceUrl",
                    "retrievedAt");
            assertThat(item.path("status").asText()).isIn("CURRENT", "EXPIRED", "SUPERSEDED", "UNKNOWN");
            assertThat(item.path("categories").isArray()).isTrue();
        }

        JsonNode services = requiredArray(DATA.resolve("wenchang-public-services.json"), "services");
        assertThat(services).isNotEmpty();
        assertUniqueIdsAndRequiredSourceIds(services);
        for (JsonNode item : services) {
            assertText(item, "name", "category", "town", "address", "description", "serviceScope",
                    "sourceOrganization", "sourceUrl", "retrievedAt");
            assertThat(item.path("latitude").isNumber()).as(item.path("name").asText()).isTrue();
            assertThat(item.path("longitude").isNumber()).as(item.path("name").asText()).isTrue();
        }

        PolicySearchTool tool = new PolicySearchTool(DATA.resolve("wenchang-policies.json").toString());
        assertThat(tool.search("商业航天", "", "", 10)).isNotEmpty()
                .allSatisfy(policy -> {
                    assertThat(policy.sourceId()).startsWith("SRC-");
                    assertThat(policy.organization()).isNotBlank();
                    assertThat(policy.sourceUrl()).startsWith("http");
                    assertThat(policy.status()).isIn("CURRENT", "EXPIRED", "SUPERSEDED", "UNKNOWN");
                });
    }

    @Test
    void everyStructuredAssetSourceIdIsCataloguedInSourcesIndex() throws IOException {
        Set<String> catalogued = new HashSet<>();
        List<String> lines = Files.readAllLines(Path.of("knowledge", "SOURCES_INDEX.csv"));
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (!line.isEmpty()) catalogued.add(line.substring(0, line.indexOf(',')));
        }

        Set<String> used = new HashSet<>();
        for (String asset : List.of("wenchang-places.json", "wenchang-policies.json",
                "wenchang-public-services.json")) {
            JsonNode root = MAPPER.readTree(DATA.resolve(asset).toFile());
            JsonNode records = root.path("places").isArray() ? root.path("places")
                    : root.path("policies").isArray() ? root.path("policies") : root.path("services");
            records.forEach(item -> used.add(item.path("sourceId").asText()));
        }
        assertThat(used).doesNotContain("");
        assertThat(catalogued).containsAll(used);
    }

    private static JsonNode requiredArray(Path file, String key) throws IOException {
        assertThat(Files.isRegularFile(file)).as("missing structured asset %s", file).isTrue();
        JsonNode root = MAPPER.readTree(file.toFile());
        JsonNode array = root.isArray() ? root : root.path(key);
        assertThat(array.isArray()).as("%s must contain array '%s'", file, key).isTrue();
        return array;
    }

    private static void assertUniqueIdsAndRequiredSourceIds(JsonNode array) {
        Set<String> ids = new HashSet<>();
        for (JsonNode item : array) {
            assertText(item, "id", "sourceId");
            assertThat(ids.add(item.path("id").asText())).as("duplicate id %s", item.path("id").asText()).isTrue();
        }
    }

    private static void assertText(JsonNode item, String... fields) {
        for (String field : fields) {
            assertThat(item.path(field).isTextual() && !item.path(field).asText().isBlank())
                    .as("%s missing %s", item.path("id").asText("record"), field).isTrue();
        }
    }
}
