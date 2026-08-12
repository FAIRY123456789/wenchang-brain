package cn.wenchang.brain.workflow;

import java.util.List;

public record AgentRunStep(String id, String title, String stage, List<String> tools) {
    public AgentRunStep {
        if (id == null || id.isBlank() || title == null || title.isBlank() || stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("step id, title and stage are required");
        }
        tools = List.copyOf(tools);
    }
}
