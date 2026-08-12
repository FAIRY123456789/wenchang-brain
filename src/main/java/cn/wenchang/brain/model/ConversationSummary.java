package cn.wenchang.brain.model;

import java.time.Instant;

public record ConversationSummary(String id, String title, Instant createdAt, Instant updatedAt, String agentId) {
    public ConversationSummary(String id, String title, Instant createdAt, Instant updatedAt) {
        this(id, title, createdAt, updatedAt, "wenchang");
    }
}
