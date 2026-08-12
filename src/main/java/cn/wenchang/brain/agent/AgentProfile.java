package cn.wenchang.brain.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 面向一次会话的智能体能力画像。
 *
 * <p>Profile 只组合提示词、知识偏好、工具偏好和输出风格，不复制执行器。</p>
 */
public record AgentProfile(
        String id,
        String displayName,
        String description,
        String icon,
        List<String> knowledgeCategories,
        List<String> preferredTools,
        String systemInstruction,
        List<String> suggestedSkills,
        String responseStyle,
        List<String> taskCapabilities,
        List<String> acceptedInputs,
        List<String> artifactTypes,
        List<String> typicalWorkflow,
        String humanInTheLoop,
        List<String> exampleTasks,
        String completionCriteria,
        String contextSummary
) {
    public AgentProfile {
        id = required(id, "id");
        displayName = required(displayName, "displayName");
        description = required(description, "description");
        icon = required(icon, "icon");
        knowledgeCategories = List.copyOf(knowledgeCategories);
        preferredTools = List.copyOf(preferredTools);
        systemInstruction = required(systemInstruction, "systemInstruction");
        suggestedSkills = List.copyOf(suggestedSkills);
        responseStyle = required(responseStyle, "responseStyle");
        taskCapabilities = List.copyOf(taskCapabilities);
        acceptedInputs = List.copyOf(acceptedInputs);
        artifactTypes = List.copyOf(artifactTypes);
        typicalWorkflow = List.copyOf(typicalWorkflow);
        humanInTheLoop = required(humanInTheLoop, "humanInTheLoop");
        exampleTasks = List.copyOf(exampleTasks);
        completionCriteria = required(completionCriteria, "completionCriteria");
        contextSummary = required(contextSummary, "contextSummary");
    }

    @JsonProperty("displayNameEn")
    public String displayNameEn() { return displayName; }

    @JsonProperty("descriptionZh")
    public String descriptionZh() { return description; }

    @JsonProperty("capabilitiesZh")
    public List<String> capabilitiesZh() { return taskCapabilities; }

    @JsonProperty("acceptedInputsZh")
    public List<String> acceptedInputsZh() { return acceptedInputs; }

    @JsonProperty("skills")
    public List<String> skills() { return suggestedSkills; }

    @JsonProperty("tools")
    public List<String> tools() { return preferredTools; }

    @JsonProperty("workflowZh")
    public List<String> workflowZh() { return typicalWorkflow; }

    @JsonProperty("humanApprovalZh")
    public String humanApprovalZh() { return humanInTheLoop; }

    @JsonProperty("examplesZh")
    public List<String> examplesZh() { return exampleTasks; }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
