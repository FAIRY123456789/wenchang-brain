package cn.wenchang.brain.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agent_approval")
public class AgentApprovalEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(length = 36)
    private String agentRunId;
    @Column(length = 128, nullable = false)
    private String conversationId;
    @Column(length = 40, nullable = false)
    private String agentId;
    @Column(length = 60)
    private String skillId;
    @Column(length = 48, nullable = false)
    private String actionType;
    @Column(length = 120, nullable = false)
    private String operation;
    @Column(length = 300, nullable = false)
    private String impactScope;
    @Lob
    private String payloadJson;
    @Column(length = 24, nullable = false)
    private String status;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant decidedAt;
    @Lob
    private String resultSummary;

    protected AgentApprovalEntity() { }

    public AgentApprovalEntity(String id, String agentRunId, String conversationId, String agentId,
                               String skillId, String actionType, String operation, String impactScope,
                               String payloadJson, Instant createdAt) {
        this.id = id;
        this.agentRunId = agentRunId;
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.skillId = skillId;
        this.actionType = actionType;
        this.operation = operation;
        this.impactScope = impactScope;
        this.payloadJson = payloadJson;
        this.status = "PENDING";
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getAgentRunId() { return agentRunId; }
    public String getConversationId() { return conversationId; }
    public String getAgentId() { return agentId; }
    public String getSkillId() { return skillId; }
    public String getActionType() { return actionType; }
    public String getOperation() { return operation; }
    public String getImpactScope() { return impactScope; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getResultSummary() { return resultSummary; }

    public void decide(String status, String resultSummary) {
        this.status = status;
        this.resultSummary = resultSummary;
        this.decidedAt = Instant.now();
    }
}
