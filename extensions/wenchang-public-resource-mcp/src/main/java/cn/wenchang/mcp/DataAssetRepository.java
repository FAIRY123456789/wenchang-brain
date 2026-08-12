package cn.wenchang.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class DataAssetRepository {

    private static final Logger log = LoggerFactory.getLogger(DataAssetRepository.class);

    private final PublicResourceProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.empty());

    public DataAssetRepository(PublicResourceProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void load() {
        Path dataRoot = resolveDataRoot();
        Asset services = readAsset(dataRoot.resolve(properties.getPublicServicesFile()),
                List.of("services", "publicServices", "public_services"));
        Asset townships = readAsset(dataRoot.resolve(properties.getTownshipsFile()),
                List.of("townships", "administrativeUnits", "administrative_units", "units"));
        Asset places = readAsset(dataRoot.resolve(properties.getPlacesFile()), List.of("places"));
        current.set(new Snapshot(dataRoot, services, townships, places, Instant.now()));
        log.info("公共资源数据加载完成：root={}, services={}, townships={}, places={}",
                dataRoot, services.records().size(), townships.records().size(), places.records().size());
    }

    public Snapshot snapshot() {
        return current.get();
    }

    private Path resolveDataRoot() {
        if (properties.getDataRoot() != null && !properties.getDataRoot().toString().isBlank()) {
            return properties.getDataRoot().toAbsolutePath().normalize();
        }
        List<Path> candidates = List.of(Path.of("data"), Path.of("..", "..", "data"));
        return candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(candidates.get(0).toAbsolutePath().normalize());
    }

    private Asset readAsset(Path path, List<String> arrayKeys) {
        if (!Files.isRegularFile(path)) {
            log.warn("结构化数据文件不存在：{}", path);
            return new Asset(path, List.of(), "MISSING", null);
        }
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            JsonNode records = root;
            if (!records.isArray()) {
                records = arrayKeys.stream().map(root::path).filter(JsonNode::isArray).findFirst()
                        .orElse(objectMapper.createArrayNode());
            }
            List<JsonNode> values = new ArrayList<>();
            records.forEach(values::add);
            return new Asset(path, List.copyOf(values), "READY", Files.getLastModifiedTime(path).toInstant());
        }
        catch (IOException ex) {
            log.error("结构化数据文件读取失败：{}", path, ex);
            return new Asset(path, List.of(), "ERROR", null);
        }
    }

    public record Asset(Path path, List<JsonNode> records, String status, Instant lastModifiedAt) {}

    public record Snapshot(Path dataRoot, Asset publicServices, Asset townships, Asset places, Instant loadedAt) {
        static Snapshot empty() {
            Asset empty = new Asset(Path.of("."), List.of(), "NOT_LOADED", null);
            return new Snapshot(Path.of("."), empty, empty, empty, Instant.EPOCH);
        }

        public Map<String, Object> statusDetails() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("dataRoot", dataRoot.toString());
            details.put("publicServices", publicServices.records().size());
            details.put("publicServicesStatus", publicServices.status());
            details.put("townships", townships.records().size());
            details.put("townshipsStatus", townships.status());
            details.put("places", places.records().size());
            details.put("placesStatus", places.status());
            details.put("loadedAt", loadedAt.toString());
            return details;
        }
    }
}
