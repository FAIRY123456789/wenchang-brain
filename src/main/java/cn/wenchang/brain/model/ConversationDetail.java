package cn.wenchang.brain.model;

import java.time.Instant;
import java.util.List;

public record ConversationDetail(
        String id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        String agentId,
        List<PersistedMessageDto> messages
) {
    public ConversationDetail(String id, String title, Instant createdAt, Instant updatedAt,
                              List<PersistedMessageDto> messages) {
        this(id, title, createdAt, updatedAt, "wenchang", messages);
    }
}
