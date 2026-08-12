package cn.wenchang.brain.workflow;

import java.util.List;

/** 可向用户公开的任务计划；不包含模型内部推理。 */
public record AgentRunPlan(String workflowType, List<AgentRunStep> steps) {
    public AgentRunPlan {
        if (workflowType == null || workflowType.isBlank()) throw new IllegalArgumentException("workflowType required");
        steps = List.copyOf(steps);
        if (steps.size() < 1 || steps.size() > 6) throw new IllegalArgumentException("plan must contain 1 to 6 steps");
    }
}
