package cn.wenchang.brain.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts tool protocol payloads into a concise, human-readable report. */
@org.springframework.stereotype.Component
public final class ArtifactReportComposer {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public ComposedReport compose(String request, Map<String, String> toolOutputs) {
        String question = request == null ? "" : request.trim();
        List<Map<String, String>> records = extractRecords(toolOutputs);
        List<Map<String, String>> selected = selectRecords(question, records);
        String title = professionalTitle(question, selected);
        List<String> sources = sources(selected.isEmpty() ? records : selected);
        return new ComposedReport(title, reportTopic(question), content(question, records, selected), sources);
    }

    public String professionalTitle(String request) {
        return professionalTitle(request == null ? "" : request, List.of());
    }

    private String professionalTitle(String request, List<Map<String, String>> records) {
        if (request.matches("(?s).*(高中|高一|高二|高三).*(名单|学校|教育).*")) {
            return "文昌市高中阶段学校名单与信息核验报告";
        }
        if (request.matches("(?s).*(小学|小学生).*(名单|学校|教育).*")) return "文昌市小学教育资源名单";
        if (request.matches("(?s).*(中学|初中).*(名单|学校|教育).*")) return "文昌市中学教育资源名单";
        if (request.matches("(?s).*(学校|教育).*(名单|报告).*")) return "文昌市教育资源名单";
        if (request.matches("(?s).*(医院|医疗|卫生).*(名单|报告).*")) return "文昌市医疗服务资源名单";
        if (request.matches("(?s).*(政策).*(清单|报告|简报).*")) return "文昌市相关政策整理报告";
        if (request.matches("(?s).*(研学地点|研学资源).*(名单|报告).*")) return "文昌市研学资源名录";
        if (!records.isEmpty()) return "文昌市专题资料整理报告";
        return "文昌专题研究报告";
    }

    private String reportTopic(String request) {
        if (request.matches("(?s).*(高中|学校|教育).*")) return "教育资源整理与来源核验";
        if (request.matches("(?s).*(政策).*")) return "政策资料整理与来源核验";
        if (request.matches("(?s).*(研学).*")) return "研学资源整理与来源核验";
        if (request.matches("(?s).*(医院|医疗|卫生).*")) return "公共医疗资源整理与来源核验";
        return "公开资料整理与来源核验";
    }

    private String content(String request, List<Map<String, String>> all, List<Map<String, String>> selected) {
        StringBuilder report = new StringBuilder();
        report.append("## 报告说明\n")
                .append("本报告根据本次任务实际取得的公开资料整理，已移除工具协议、JSON 字段和无关技术信息。")
                .append("名单仅反映当前可核验的公开记录，不替代主管部门的正式名录或招生公告。\n\n");
        if (all.isEmpty()) {
            report.append("## 检索结果\n本次未取得可结构化整理的有效记录，因此不编造名单条目。\n\n")
                    .append("## 后续核验建议\n- 优先查询文昌市教育、卫生或相关主管部门发布的正式名录。\n")
                    .append("- 对名称、办学层级、地址和开放状态逐项复核。\n");
            return report.toString();
        }

        List<Map<String, String>> rows = selected.isEmpty() ? all : selected;
        report.append("## 名单概览\n")
                .append("本次共取得 ").append(all.size()).append(" 条公开记录，按任务条件整理出 ")
                .append(rows.size()).append(" 条候选记录。\n\n")
                .append("## 资源名单\n")
                .append("| 序号 | 名称 | 所在乡镇 | 地址 | 服务或办学信息 |\n")
                .append("|---:|---|---|---|---|\n");
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            report.append('|').append(index + 1).append('|')
                    .append(tableText(first(row, "name", "title"), "未命名记录")).append('|')
                    .append(tableText(row.get("town"), "—")).append('|')
                    .append(tableText(row.get("address"), "—")).append('|')
                    .append(tableText(first(row, "serviceScope", "summary", "description"), "以主管单位公布信息为准"))
                    .append("|\n");
        }
        report.append("\n## 信息核验提示\n");
        if (request.matches("(?s).*(高中|高一|高二|高三).*")) {
            report.append("- 学校名称含“中学”不等同于已确认开设高中学段；高中办学资格、招生范围和招生计划须以教育部门最新公告为准。\n");
        }
        report.append("- 地址与坐标类信息可能来自公开地理数据，仅用于定位参考。\n")
                .append("- 开放状态、招生安排和服务范围可能变化，使用前应打开原始来源再次核验。\n");
        return report.toString();
    }

    private List<Map<String, String>> selectRecords(String request, List<Map<String, String>> records) {
        if (request.matches("(?s).*(高中|高一|高二|高三).*")) {
            return records.stream().filter(row -> {
                String name = first(row, "name", "title");
                return name.contains("中学") && !name.contains("小学")
                        && !name.matches(".*(职业学院|学院|大学)$");
            }).toList();
        }
        if (request.matches("(?s).*(小学|小学生).*")) {
            return records.stream().filter(row -> first(row, "name", "title").contains("小学")).toList();
        }
        return records;
    }

    private List<Map<String, String>> extractRecords(Map<String, String> outputs) {
        List<Map<String, String>> records = new ArrayList<>();
        if (outputs == null) return records;
        outputs.values().forEach(value -> collect(parse(value), records, 0));
        Map<String, Map<String, String>> unique = new LinkedHashMap<>();
        for (Map<String, String> record : records) {
            String key = first(record, "id", "sourceId", "name", "title", "sourceUrl", "url");
            if (!key.isBlank()) unique.putIfAbsent(key, record);
        }
        return List.copyOf(unique.values());
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) return mapper.nullNode();
        try { return mapper.readTree(value); }
        catch (Exception ignored) { return mapper.getNodeFactory().textNode(value); }
    }

    private void collect(JsonNode node, List<Map<String, String>> records, int depth) {
        if (node == null || node.isNull() || depth > 8) return;
        if (node.isTextual()) {
            String text = node.asText().trim();
            if ((text.startsWith("{") || text.startsWith("[")) && text.length() < 2_000_000) {
                JsonNode parsed = parse(text);
                if (!parsed.isTextual()) collect(parsed, records, depth + 1);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collect(item, records, depth + 1));
            return;
        }
        JsonNode results = node.path("results");
        if (results.isArray()) results.forEach(item -> addRecord(item, records));
        JsonNode items = node.path("items");
        if (items.isArray()) items.forEach(item -> addRecord(item, records));
        node.fields().forEachRemaining(entry -> {
            if (!Set.of("results", "items").contains(entry.getKey())) collect(entry.getValue(), records, depth + 1);
        });
    }

    private void addRecord(JsonNode node, List<Map<String, String>> records) {
        if (!node.isObject()) return;
        Map<String, String> row = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isValueNode()) row.put(entry.getKey(), value.asText());
            else if (value.isArray()) {
                List<String> values = new ArrayList<>();
                value.forEach(item -> { if (item.isValueNode()) values.add(item.asText()); });
                row.put(entry.getKey(), String.join("、", values));
            }
        });
        if (!row.isEmpty()) records.add(Map.copyOf(row));
    }

    private List<String> sources(List<Map<String, String>> records) {
        Set<String> sources = new LinkedHashSet<>();
        for (Map<String, String> row : records) {
            String url = first(row, "sourceUrl", "url");
            if (!url.matches("(?i)^https?://.+")) continue;
            String organization = first(row, "sourceOrganization", "organization");
            sources.add((organization.isBlank() ? "公开来源" : organization) + " - " + url);
        }
        return List.copyOf(sources);
    }

    private String tableText(String value, String fallback) {
        String text = value == null || value.isBlank() ? fallback : value;
        return text.replace('|', '／').replaceAll("[\\r\\n]+", " ").trim();
    }

    private String first(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    public record ComposedReport(String title, String topic, String content, List<String> sources) { }
}
