package cn.wenchang.brain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 读取资料采集主管维护的官方来源注册表。
 *
 * <p>注册表是官方域名的唯一信任根。工具不会从搜索摘要猜测机构，也不会自动把搜索结果域名
 * 写回注册表。文件不存在时返回空列表，使没有资料或没有 MCP 的开发环境仍可正常启动。</p>
 */
@Component
public class OfficialSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(OfficialSourceRegistry.class);

    private final Path registryFile;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private volatile Snapshot snapshot = new Snapshot(null, List.of());

    public OfficialSourceRegistry(
            @Value("${wenchang.official-source-registry-file:data/official-source-registry.json}") String file) {
        this.registryFile = Path.of(file).toAbsolutePath().normalize();
    }

    public List<OfficialSource> sources() {
        try {
            if (!Files.isRegularFile(registryFile)) return List.of();
            FileTime modified = Files.getLastModifiedTime(registryFile);
            Snapshot current = snapshot;
            if (modified.equals(current.modifiedAt())) return current.sources();
            synchronized (this) {
                current = snapshot;
                if (modified.equals(current.modifiedAt())) return current.sources();
                List<OfficialSource> loaded = load();
                snapshot = new Snapshot(modified, loaded);
                log.info("Official source registry loaded: file={} sources={}", registryFile, loaded.size());
                return loaded;
            }
        } catch (Exception exception) {
            log.warn("Official source registry unavailable: file={} error={}", registryFile,
                    exception.getClass().getSimpleName());
            return List.of();
        }
    }

    public List<OfficialSource> candidates(String query, int limit) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return sources().stream()
                .sorted(Comparator.comparingInt((OfficialSource source) -> relevance(normalized, source)).reversed()
                        .thenComparing(OfficialSource::level)
                        .thenComparing(OfficialSource::name))
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<OfficialSource> load() throws Exception {
        JsonNode root = objectMapper.readTree(registryFile.toFile());
        JsonNode entries = root.isArray() ? root : root.path("sources");
        if (!entries.isArray()) return List.of();
        List<OfficialSource> result = new ArrayList<>();
        for (JsonNode node : entries) {
            String name = text(node, "name", "organization", "source_organization");
            String domain = normalizeDomain(text(node, "domain"));
            String category = textOrArray(node, "category", "categories");
            String level = text(node, "level", "source_level");
            boolean includeSubdomains = node.path("includeSubdomains").asBoolean(false);
            if (!name.isBlank() && !domain.isBlank()) {
                result.add(new OfficialSource(name, domain, category, level.isBlank() ? "P0" : level,
                        includeSubdomains));
            }
        }
        return List.copyOf(result);
    }

    private int relevance(String query, OfficialSource source) {
        if (query.isBlank()) return 0;
        int score = 0;
        if (containsAny(query, source.category())) score += 8;
        if (containsAny(query, source.name())) score += 6;
        if (query.contains("航天") && containsAny(source.category() + source.name(), "航天 发射 卫星")) score += 12;
        if (query.contains("航天") && source.category().contains("aerospace")) score += 12;
        if (query.matches(".*(生态|环境|红树林|湿地|海洋|自然资源).*"))
            if (containsAny(source.category() + source.name(), "生态 环境 自然资源 海洋")) score += 12;
        if (query.matches(".*(生态|环境|红树林|湿地|海洋|自然资源).*")
                && source.category().matches(".*(ecology|environment|natural_resource|ocean).*")) score += 12;
        if (query.matches(".*(统计|人口|经济|行政数据).*"))
            if (containsAny(source.category() + source.name(), "统计 政府")) score += 12;
        if (query.matches(".*(统计|人口|经济|行政数据).*")
                && source.category().matches(".*(statistics|government).*")) score += 12;
        if (query.matches(".*(教育|学校|科研).*"))
            if (containsAny(source.category() + source.name(), "教育 科技")) score += 10;
        if (query.matches(".*(教育|学校|科研).*") && source.category().matches(".*(education|science).*")) score += 10;
        if (query.matches(".*(政策|政府|项目|公告).*") && source.category().contains("government")) score += 8;
        return score;
    }

    private boolean containsAny(String haystack, String terms) {
        if (haystack == null || terms == null) return false;
        String normalized = haystack.toLowerCase(Locale.ROOT);
        for (String term : terms.split("[\\s,，/;；|]+")) {
            if (term.length() >= 2 && normalized.contains(term.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String normalizeDomain(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        try {
            URI uri = URI.create(value.matches("^[a-z][a-z0-9+.-]*://.*") ? value : "https://" + value);
            String host = uri.getHost();
            if (host == null) return "";
            return host.replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isTextual() && !value.asText().isBlank()) return value.asText().trim();
        }
        return "";
    }

    private String textOrArray(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isTextual()) return value.asText().trim();
            if (value.isArray()) {
                List<String> parts = new ArrayList<>();
                value.forEach(item -> { if (item.isTextual()) parts.add(item.asText().trim()); });
                if (!parts.isEmpty()) return String.join(" ", parts);
            }
        }
        return "";
    }

    public Path registryFile() { return registryFile; }

    public record OfficialSource(String name, String domain, String category, String level,
                                 boolean includeSubdomains) { }
    private record Snapshot(FileTime modifiedAt, List<OfficialSource> sources) { }
}
