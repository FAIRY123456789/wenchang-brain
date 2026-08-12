package cn.wenchang.mcp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class ProductionArtifactTools {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ArtifactStore artifactStore;
    private final WordArtifactWriter wordWriter;
    private final TabularArtifactWriter tabularWriter;
    private final TaskDatasetRepository datasets;

    public ProductionArtifactTools(ArtifactStore artifactStore, WordArtifactWriter wordWriter,
                                   TabularArtifactWriter tabularWriter, TaskDatasetRepository datasets) {
        this.artifactStore = artifactStore;
        this.wordWriter = wordWriter;
        this.tabularWriter = tabularWriter;
        this.datasets = datasets;
    }

    @Tool(name = "createWenchangWordReport", description = "将标题、主题、正文和来源生成真实可下载的文昌专题 Word（.docx）报告。用户已经明确要求生成 Word 时调用。")
    public ArtifactResult createWenchangWordReport(
            @ToolParam(description = "报告标题", required = true) String title,
            @ToolParam(description = "报告主题", required = true) String topic,
            @ToolParam(description = "正文；可使用 #、##、###、项目符号和编号分段", required = true) String content,
            @ToolParam(description = "来源说明或来源 URL 列表", required = false) List<String> sources,
            @ToolParam(description = "当前会话 ID；缺失时归入 unassigned", required = false) String conversationId,
            @ToolParam(description = "创建文件的 Agent ID", required = false) String createdByAgent,
            @ToolParam(description = "触发文件生成的 Skill ID", required = false) String skillId) {
        List<String> normalizedSources = distinct(sources);
        String filename = filename(title, "文昌专题报告", ".docx");
        ArtifactManifest manifest = artifactStore.create(conversationId, "WORD", filename, DOCX_CONTENT_TYPE,
                createdByAgent, skillId, normalizedSources.size(),
                path -> wordWriter.write(path, title, topic, content, normalizedSources));
        return result(manifest, "Word 报告已生成，可直接打开或下载。", normalizedSources.size());
    }

    @Tool(name = "exportWenchangData", description = "把文昌地点、政策、公共服务或来源数据按字段和筛选条件导出为 CSV 或 XLSX 文件。")
    public ArtifactResult exportWenchangData(
            @ToolParam(description = "数据集类型：places、policies、publicServices、sources", required = true)
            String datasetType,
            @ToolParam(description = "需要导出的字段；空列表使用该数据集的标准字段", required = false)
            List<String> fields,
            @ToolParam(description = "字段到筛选值的映射；采用包含匹配", required = false)
            Map<String, Object> filters,
            @ToolParam(description = "导出格式：csv 或 xlsx", required = true) String format,
            @ToolParam(description = "当前会话 ID；缺失时归入 unassigned", required = false) String conversationId,
            @ToolParam(description = "创建文件的 Agent ID", required = false) String createdByAgent,
            @ToolParam(description = "触发文件生成的 Skill ID", required = false) String skillId) {
        String normalizedFormat = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("csv", "xlsx").contains(normalizedFormat)) {
            throw new IllegalArgumentException("format must be csv or xlsx");
        }
        TaskDatasetRepository.ExportDataset dataset = datasets.export(datasetType, fields, filters);
        String filename = filename("文昌" + datasetLabel(dataset.type()) + "数据", "文昌数据", "." + normalizedFormat);
        String contentType = normalizedFormat.equals("csv") ? "text/csv;charset=UTF-8" : XLSX_CONTENT_TYPE;
        ArtifactManifest manifest = artifactStore.create(conversationId, normalizedFormat.toUpperCase(Locale.ROOT),
                filename, contentType, createdByAgent, skillId, 0, path -> {
                    if (normalizedFormat.equals("csv")) tabularWriter.writeCsv(path, dataset.fields(), dataset.rows());
                    else tabularWriter.writeXlsx(path, dataset.fields(), dataset.rows());
                });
        return result(manifest, "已导出 " + dataset.rows().size() + " 行" + datasetLabel(dataset.type()) + "数据。", 0);
    }

    @Tool(name = "createStudyTourPackage", description = "根据年龄、时长、主题和偏好检索文昌真实地点并生成可下载的 Word 研学方案。")
    public ArtifactResult createStudyTourPackage(
            @ToolParam(description = "学生年龄段，例如小学、初中、高中", required = true) String ageGroup,
            @ToolParam(description = "研学时长，例如半天、一天、两天", required = true) String duration,
            @ToolParam(description = "研学主题，例如航天、生态、历史", required = true) List<String> themes,
            @ToolParam(description = "人数、交通、兴趣等偏好", required = false) List<String> preferences,
            @ToolParam(description = "当前会话 ID；缺失时归入 unassigned", required = false) String conversationId,
            @ToolParam(description = "创建文件的 Agent ID", required = false) String createdByAgent,
            @ToolParam(description = "触发文件生成的 Skill ID", required = false) String skillId) {
        List<Map<String, String>> places = datasets.studyTourPlaces(ageGroup, themes, preferences);
        if (places.isEmpty()) places = datasets.studyTourPlaces(ageGroup, List.of(), List.of());
        List<String> sources = places.stream().map(this::sourceOf).filter(value -> !value.isBlank()).distinct().toList();
        String themeText = listText(themes, "文昌综合研学");
        String title = "文昌" + themeText.replace("、", "与") + "研学方案";
        String content = studyTourContent(ageGroup, duration, themes, preferences, places);
        ArtifactManifest manifest = artifactStore.create(conversationId, "STUDY_TOUR_WORD",
                filename(title, "文昌研学方案", ".docx"), DOCX_CONTENT_TYPE,
                defaultAgent(createdByAgent, "study-tour"), defaultSkill(skillId, "study-tour-package"), sources.size(),
                path -> wordWriter.write(path, title, themeText, content, sources));
        return result(manifest, "已形成包含 " + places.size() + " 个真实地点的研学方案。", sources.size());
    }

    @Tool(name = "createPolicyBrief", description = "检索文昌及海南结构化政策，按主题、时间和关注点生成带官方来源的 Word 政策简报。")
    public ArtifactResult createPolicyBrief(
            @ToolParam(description = "政策主题", required = true) String topic,
            @ToolParam(description = "时间范围，例如 2024-2026；空值表示不限", required = false) String timeRange,
            @ToolParam(description = "关注点，例如商业航天、产业、人才", required = false) String focus,
            @ToolParam(description = "当前会话 ID；缺失时归入 unassigned", required = false) String conversationId,
            @ToolParam(description = "创建文件的 Agent ID", required = false) String createdByAgent,
            @ToolParam(description = "触发文件生成的 Skill ID", required = false) String skillId) {
        List<Map<String, String>> policies = datasets.policies(topic, timeRange, focus);
        if (policies.isEmpty()) policies = datasets.policies("", timeRange, "");
        List<String> sources = policies.stream().map(this::sourceOf).filter(value -> !value.isBlank()).distinct().toList();
        String title = (topic == null || topic.isBlank() ? "文昌政策" : topic.trim()) + "政策简报";
        String content = policyContent(topic, timeRange, focus, policies);
        ArtifactManifest manifest = artifactStore.create(conversationId, "POLICY_BRIEF_WORD",
                filename(title, "文昌政策简报", ".docx"), DOCX_CONTENT_TYPE,
                defaultAgent(createdByAgent, "policy"), defaultSkill(skillId, "policy-brief"), sources.size(),
                path -> wordWriter.write(path, title, "政策研究与证据整理", content, sources));
        return result(manifest, "已整理 " + policies.size() + " 项政策并生成 Word 简报。", sources.size());
    }

    private String studyTourContent(String ageGroup, String duration, List<String> themes, List<String> preferences,
                                    List<Map<String, String>> places) {
        StringBuilder content = new StringBuilder();
        content.append("## 研学主题\n").append(listText(themes, "文昌综合研学")).append("\n\n")
                .append("## 适合对象\n").append(defaultText(ageGroup, "待确认")).append("\n\n")
                .append("## 时间安排\n").append(defaultText(duration, "待确认")).append("\n\n")
                .append("## 学习目标\n")
                .append("- 认识文昌真实场所承载的航天、生态、历史或公共文化知识\n")
                .append("- 能够根据现场证据记录观察结果并核验来源\n")
                .append("- 建立安全、预约和开放状态动态核验意识\n\n")
                .append("## 路线顺序\n");
        if (places.isEmpty()) content.append("1. 暂无满足条件且可追溯的地点，需补充条件后重新规划。\n");
        IntStream.range(0, places.size()).forEach(index -> {
            Map<String, String> place = places.get(index);
            content.append(index + 1).append(". ").append(place.getOrDefault("name", "未命名地点"))
                    .append("（").append(place.getOrDefault("town", "文昌")).append("）\n");
        });
        content.append("\n## 地点与学习内容\n");
        for (Map<String, String> place : places) {
            content.append("### ").append(place.getOrDefault("name", "地点")).append("\n")
                    .append(defaultText(place.get("summary"), "以现场公开信息为准。")).append("\n")
                    .append("- 学习内容：").append(defaultText(place.get("learningPoints"), "现场观察与资料核验")).append("\n")
                    .append("- 适龄提示：").append(defaultText(place.get("suitableAge"), "需结合学生情况确认")).append("\n")
                    .append("- 访问约束：").append(defaultText(place.get("accessType"), "出发前核验预约、交通与开放状态")).append("\n\n");
        }
        content.append("## 偏好与组织提示\n").append(listText(preferences, "无额外偏好")).append("\n\n")
                .append("## 注意事项\n")
                .append("- 出发前向场馆或主管单位确认预约、开放时间和团队容量。\n")
                .append("- 航天发射、滨海天气、交通和安全管制均可能动态变化。\n")
                .append("- 路线顺序是基于主题的建议，不虚构精确行车时间。\n");
        return content.toString();
    }

    private String policyContent(String topic, String timeRange, String focus, List<Map<String, String>> policies) {
        StringBuilder content = new StringBuilder();
        content.append("## 摘要\n围绕“").append(defaultText(topic, "文昌相关政策")).append("”整理结构化政策资料；")
                .append("时间范围：").append(defaultText(timeRange, "不限")).append("；关注点：")
                .append(defaultText(focus, "综合影响")).append("。\n\n## 政策列表\n");
        if (policies.isEmpty()) content.append("未找到满足条件的结构化政策，不能编造政策条目。\n");
        for (Map<String, String> policy : policies) {
            content.append("### ").append(policy.getOrDefault("title", "未命名政策")).append("\n")
                    .append("- 发布单位：").append(defaultText(policy.get("organization"), "待核验")).append("\n")
                    .append("- 发布日期：").append(defaultText(policy.get("publishedAt"), "未记录")).append("\n")
                    .append("- 状态：").append(defaultText(policy.get("status"), "UNKNOWN")).append("\n")
                    .append("- 主要内容：").append(defaultText(policy.get("summary"), "详见官方原文")).append("\n")
                    .append("- 对文昌的相关性：").append(policyRelevance(policy, topic, focus)).append("\n\n");
        }
        content.append("## 更新时间\n以本次文件生成时间为准；政策效力和最新动态应再次打开官方原文核验。\n");
        return content.toString();
    }

    private String policyRelevance(Map<String, String> policy, String topic, String focus) {
        String text = String.join(" ", policy.values());
        List<String> matched = List.of(topic, focus).stream().filter(value -> value != null && !value.isBlank())
                .filter(text::contains).toList();
        return matched.isEmpty() ? "属于文昌或海南政策资料，具体相关性需结合项目范围核验。"
                : "与“" + String.join("、", matched) + "”直接相关。";
    }

    private ArtifactResult result(ArtifactManifest manifest, String summary, int sourceCount) {
        return new ArtifactResult(manifest.id(), manifest.filename(), manifest.downloadUrl(), manifest.type(),
                manifest.conversationId(), manifest.createdAt(), sourceCount, summary);
    }

    private String sourceOf(Map<String, String> row) {
        String organization = defaultText(row.get("sourceOrganization"), row.get("organization"));
        String url = defaultText(row.get("sourceUrl"), row.get("url"));
        if (url == null || url.isBlank()) return "";
        return (organization == null || organization.isBlank() ? "来源" : organization) + " - " + url;
    }

    private String filename(String value, String fallback, String suffix) {
        String base = defaultText(value, fallback).replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim();
        return base.endsWith(suffix) ? base : base + suffix;
    }

    private String datasetLabel(String type) {
        return switch (type) {
            case "places" -> "研学地点";
            case "policies" -> "政策";
            case "publicServices" -> "公共服务";
            case "sources" -> "来源";
            default -> "";
        };
    }

    private List<String> distinct(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }

    private String listText(List<String> values, String fallback) {
        List<String> cleaned = distinct(values);
        return cleaned.isEmpty() ? fallback : String.join("、", cleaned);
    }

    private String defaultAgent(String value, String fallback) { return defaultText(value, fallback); }
    private String defaultSkill(String value, String fallback) { return defaultText(value, fallback); }
    private String defaultText(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    public record ArtifactResult(String artifactId, String filename, String downloadUrl, String type,
                                 String conversationId, String createdAt, int sourceCount, String summary) { }
}
