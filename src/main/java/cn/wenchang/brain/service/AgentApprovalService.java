package cn.wenchang.brain.service;

import cn.wenchang.brain.model.AgentApproval;
import cn.wenchang.brain.model.AgentApprovalRequest;
import cn.wenchang.brain.persistence.AgentApprovalEntity;
import cn.wenchang.brain.persistence.AgentApprovalRepository;
import cn.wenchang.brain.rag.KnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AgentApprovalService {

    private final AgentApprovalRepository repository;
    private final KnowledgeService knowledgeService;
    private final PolicyRefreshService policyRefreshService;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public AgentApprovalService(AgentApprovalRepository repository, KnowledgeService knowledgeService,
                                PolicyRefreshService policyRefreshService) {
        this.repository = repository;
        this.knowledgeService = knowledgeService;
        this.policyRefreshService = policyRefreshService;
    }

    @Transactional
    public AgentApproval preview(AgentApprovalRequest request) {
        String payload;
        try { payload = mapper.writeValueAsString(request.payload() == null ? java.util.Map.of() : request.payload()); }
        catch (Exception exception) { payload = "{}"; }
        AgentApprovalEntity entity = new AgentApprovalEntity(UUID.randomUUID().toString(), request.agentRunId(),
                request.conversationId(), normalize(request.agentId(), "wenchang"), request.skillId(),
                request.actionType().trim().toUpperCase(Locale.ROOT), request.operation().trim(),
                request.impactScope().trim(), payload, Instant.now());
        return dto(repository.save(entity));
    }

    @Transactional
    public AgentApproval confirm(String id) {
        AgentApprovalEntity entity = requirePending(id);
        try {
            String result = execute(entity.getActionType());
            entity.decide("EXECUTED", result);
        } catch (Exception exception) {
            entity.decide("FAILED", safe(exception.getMessage()));
        }
        return dto(entity);
    }

    @Transactional
    public AgentApproval cancel(String id) {
        AgentApprovalEntity entity = requirePending(id);
        entity.decide("CANCELLED", "用户已取消，未修改长期系统资产");
        return dto(entity);
    }

    @Transactional(readOnly = true)
    public List<AgentApproval> list(String conversationId) {
        return repository.findAllByConversationIdOrderByCreatedAtDesc(conversationId).stream().map(this::dto).toList();
    }

    private String execute(String actionType) throws Exception {
        return switch (actionType) {
            case "REINDEX_KNOWLEDGE" -> "知识库重新索引完成：" + knowledgeService.reindex().chunks() + " 个切块";
            case "REFRESH_POLICIES" -> "政策候选刷新完成：" + policyRefreshService.refresh().newCandidates() + " 条新候选";
            default -> throw new IllegalArgumentException("不支持的长期资产操作：" + actionType);
        };
    }

    private AgentApprovalEntity requirePending(String id) {
        AgentApprovalEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审批请求不存在"));
        if (!"PENDING".equals(entity.getStatus())) throw new IllegalStateException("审批请求已处理");
        return entity;
    }

    private AgentApproval dto(AgentApprovalEntity entity) {
        return new AgentApproval(entity.getId(), entity.getAgentRunId(), entity.getConversationId(),
                entity.getAgentId(), entity.getSkillId(), entity.getActionType(), entity.getOperation(),
                entity.getImpactScope(), entity.getStatus(), entity.getCreatedAt(), entity.getDecidedAt(),
                entity.getResultSummary());
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String safe(String message) {
        if (message == null || message.isBlank()) return "执行失败";
        return message.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
    }
}
