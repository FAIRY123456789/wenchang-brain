package cn.wenchang.brain.model;

import java.util.Map;

/** 可向用户公开的 Agent 执行事件；不包含内部推理过程。 */
public record AgentRunEvent(String type, Map<String, Object> data) {
    public AgentRunEvent {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static AgentRunEvent of(String type, Map<String, Object> data) {
        return new AgentRunEvent(type, data);
    }
}
