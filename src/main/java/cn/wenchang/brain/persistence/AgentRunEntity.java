package cn.wenchang.brain.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_run")
public class AgentRunEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "conversation_id", length = 128, nullable = false)
    private String conversationId;

    @Column(length = 40, nullable = false)
    private String agentId;

    @Column(length = 60)
    private String skillId;

    @Lob
    private String goal;

    @Column(length = 32, nullable = false)
    private String status;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant completedAt;

    @OneToMany(mappedBy = "agentRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC, id ASC")
    private List<AgentStepEntity> steps = new ArrayList<>();

    protected AgentRunEntity() { }

    public AgentRunEntity(String id, String conversationId, String agentId, String skillId,
                          String goal, String status, Instant startedAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.skillId = skillId;
        this.goal = goal;
        this.status = status;
        this.startedAt = startedAt;
    }

    public String getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getAgentId() { return agentId; }
    public String getSkillId() { return skillId; }
    public String getGoal() { return goal; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<AgentStepEntity> getSteps() { return steps; }

    public void addStep(AgentStepEntity step) { steps.add(step); }
    public void complete(String status, Instant completedAt) {
        this.status = status;
        this.completedAt = completedAt;
    }
}
