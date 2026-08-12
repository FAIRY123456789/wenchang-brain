package cn.wenchang.brain.model;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "message 不能为空") String message,
        String sessionId,
        String conversationId,
        String agentId,
        String skillId
) {
    public ChatRequest(String message, String sessionId) {
        this(message, sessionId, null, "wenchang", null);
    }

    public ChatRequest(String message, String sessionId, String conversationId) {
        this(message, sessionId, conversationId, "wenchang", null);
    }

    public String effectiveConversationId() {
        return conversationId == null || conversationId.isBlank() ? sessionId : conversationId;
    }

    public String effectiveAgentId() {
        return agentId == null || agentId.isBlank() ? "wenchang" : agentId.trim();
    }

    public String effectiveSkillId() {
        return skillId == null || skillId.isBlank() ? null : skillId.trim();
    }
}
