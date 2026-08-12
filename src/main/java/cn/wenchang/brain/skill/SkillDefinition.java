package cn.wenchang.brain.skill;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 用户可直接通过 / 命令选择的可执行能力。 */
public record SkillDefinition(
        String id,
        String command,
        String displayName,
        String description,
        List<String> requiredTools,
        List<String> preferredCategories,
        WorkflowType workflowType,
        String systemInstruction,
        String group,
        String outputType,
        String artifactType,
        String approvalPolicy
) {
    public SkillDefinition {
        id = required(id, "id");
        command = required(command, "command");
        displayName = required(displayName, "displayName");
        description = required(description, "description");
        requiredTools = List.copyOf(requiredTools);
        preferredCategories = List.copyOf(preferredCategories);
        if (workflowType == null) throw new IllegalArgumentException("workflowType must not be null");
        systemInstruction = required(systemInstruction, "systemInstruction");
        group = required(group, "group");
        outputType = required(outputType, "outputType");
        artifactType = artifactType == null ? "" : artifactType.trim();
        approvalPolicy = required(approvalPolicy, "approvalPolicy");
    }

    @JsonProperty("displayNameZh")
    public String displayNameZh() { return displayName; }

    @JsonProperty("descriptionZh")
    public String descriptionZh() { return description; }

    @JsonProperty("inputHintZh")
    public String inputHintZh() {
        return switch (id) {
            case "deep-research" -> "研究主题 · 时间范围 · 期望成果";
            case "official-search" -> "明确主题 · 机构范围 · 时间范围";
            case "evidence-check" -> "待核验结论 · 已有来源";
            case "word-report" -> "报告标题 · 主题 · 内容重点";
            case "policy-brief" -> "政策主题 · 时间范围 · 关注领域";
            case "data-export" -> "数据类型 · 字段 · CSV 或 Excel";
            case "study-tour-plan" -> "年龄 · 时间 · 主题 · 人数 · 偏好";
            case "place-search" -> "地点关键词 · 乡镇 · 主题 · 年龄";
            case "public-service" -> "服务类型 · 所在乡镇 · 具体需求";
            default -> "任务主题 · 范围 · 期望输出";
        };
    }

    @JsonProperty("outputZh")
    public String outputZh() { return outputType; }

    @JsonProperty("artifactTypes")
    public List<String> artifactTypes() {
        return artifactType.isBlank() ? List.of() : List.of(artifactType);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
