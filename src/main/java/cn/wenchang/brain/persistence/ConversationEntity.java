package cn.wenchang.brain.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_conversation")
public class ConversationEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 40)
    private String ownerId;

    @Column(length = 40)
    private String agentId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "active_leaf_message_id")
    private Long activeLeafMessageId;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<MessageEntity> messages = new ArrayList<>();

    protected ConversationEntity() { }

    public ConversationEntity(String id, String title, String ownerId, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.ownerId = ownerId;
        this.agentId = "wenchang";
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getOwnerId() { return ownerId; }
    public String getAgentId() { return agentId == null || agentId.isBlank() ? "wenchang" : agentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getActiveLeafMessageId() { return activeLeafMessageId; }
    public List<MessageEntity> getMessages() { return messages; }

    public void rename(String title) { this.title = title; touch(); }
    public void selectAgent(String agentId) {
        this.agentId = agentId == null || agentId.isBlank() ? "wenchang" : agentId;
        touch();
    }
    public void touch() { this.updatedAt = Instant.now(); }
    public void activateLeaf(Long messageId) { this.activeLeafMessageId = messageId; touch(); }
}
