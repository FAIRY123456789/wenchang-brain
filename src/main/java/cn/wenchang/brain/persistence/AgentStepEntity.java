package cn.wenchang.brain.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agent_step")
public class AgentStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_run_id", nullable = false)
    private AgentRunEntity agentRun;

    @Column(name = "step_sequence", nullable = false)
    private int sequence;

    @Column(length = 160, nullable = false)
    private String name;

    @Column(length = 48)
    private String stage;

    @Column(length = 120)
    private String toolName;

    @Column(length = 16)
    private String toolSource;

    @Column(length = 32, nullable = false)
    private String status;

    private Instant startedAt;
    private Instant completedAt;
    private long latencyMs;

    @Lob
    private String summary;

    @Column(length = 80)
    private String errorType;

    @Lob
    private String errorMessage;

    @Lob
    private String inputPreview;

    protected AgentStepEntity() { }

    public AgentStepEntity(AgentRunEntity agentRun, int sequence, String name, String stage,
                           String toolName, String toolSource, String status, Instant startedAt,
                           Instant completedAt, long latencyMs, String summary, String errorType,
                           String errorMessage, String inputPreview) {
        this.agentRun = agentRun;
        this.sequence = sequence;
        this.name = name;
        this.stage = stage;
        this.toolName = toolName;
        this.toolSource = toolSource;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.latencyMs = latencyMs;
        this.summary = summary;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.inputPreview = inputPreview;
    }

    public Long getId() { return id; }
    public int getSequence() { return sequence; }
    public String getName() { return name; }
    public String getStage() { return stage; }
    public String getToolName() { return toolName; }
    public String getToolSource() { return toolSource; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getLatencyMs() { return latencyMs; }
    public String getSummary() { return summary; }
    public String getErrorType() { return errorType; }
    public String getErrorMessage() { return errorMessage; }
    public String getInputPreview() { return inputPreview; }
}
