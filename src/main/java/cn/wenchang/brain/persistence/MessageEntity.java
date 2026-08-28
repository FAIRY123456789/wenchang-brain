package cn.wenchang.brain.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "parent_message_id")
    private Long parentMessageId;

    @Column(name = "revision_group_id", length = 36)
    private String revisionGroupId;

    @Column(name = "revision_index")
    private Integer revisionIndex;

    @Column(length = 64)
    private String traceId;

    @Column(length = 40)
    private String modelProvider;

    @Column(length = 100)
    private String modelName;

    @Lob
    private String sourcesJson;

    @Lob
    @Column(name = "tools_json")
    private String toolsUsedJson;

    @Column(length = 40)
    private String agentId;

    @Column(length = 60)
    private String skillId;

    @Lob
    private String agentRunJson;

    @Lob
    private String artifactsJson;

    protected MessageEntity() { }

    public MessageEntity(ConversationEntity conversation, MessageRole role, String content, Instant createdAt) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public ConversationEntity getConversation() { return conversation; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getParentMessageId() { return parentMessageId; }
    public String getRevisionGroupId() { return revisionGroupId; }
    public Integer getRevisionIndex() { return revisionIndex; }
    public String getTraceId() { return traceId; }
    public String getModelProvider() { return modelProvider; }
    public String getModelName() { return modelName; }
    public String getSourcesJson() { return sourcesJson; }
    public String getToolsUsedJson() { return toolsUsedJson; }
    public String getToolsJson() { return toolsUsedJson; }
    public String getAgentId() { return agentId; }
    public String getSkillId() { return skillId; }
    public String getAgentRunJson() { return agentRunJson; }
    public String getArtifactsJson() { return artifactsJson; }

    public void configureLineage(Long parentMessageId, String revisionGroupId, Integer revisionIndex) {
        this.parentMessageId = parentMessageId;
        this.revisionGroupId = revisionGroupId;
        this.revisionIndex = revisionIndex;
    }

    public void ensureRevisionIdentity(String groupId, int index) {
        if (role != MessageRole.USER) return;
        if (revisionGroupId == null || revisionGroupId.isBlank()) revisionGroupId = groupId;
        if (revisionIndex == null || revisionIndex < 1) revisionIndex = index;
    }

    public void attachUserContext(String agentId, String skillId) {
        this.agentId = agentId;
        this.skillId = skillId;
    }

    public void attachAssistantMetadata(String traceId, String modelProvider, String modelName,
                                        String sourcesJson, String toolsUsedJson,
                                        String agentId, String skillId, String agentRunJson, String artifactsJson) {
        this.traceId = traceId;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.sourcesJson = sourcesJson;
        this.toolsUsedJson = toolsUsedJson;
        this.agentId = agentId;
        this.skillId = skillId;
        this.agentRunJson = agentRunJson;
        this.artifactsJson = artifactsJson;
    }

    public void attachAssistantMetadata(String traceId, String modelProvider, String modelName,
                                        String sourcesJson, String toolsUsedJson,
                                        String agentId, String skillId, String agentRunJson) {
        attachAssistantMetadata(traceId, modelProvider, modelName, sourcesJson, toolsUsedJson,
                agentId, skillId, agentRunJson, "[]");
    }

    public void attachAssistantMetadata(String traceId, String modelProvider, String modelName,
                                        String sourcesJson, String toolsUsedJson) {
        attachAssistantMetadata(traceId, modelProvider, modelName, sourcesJson, toolsUsedJson,
                "wenchang", null, null, "[]");
    }
}