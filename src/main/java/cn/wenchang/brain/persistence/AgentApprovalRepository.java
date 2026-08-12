package cn.wenchang.brain.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentApprovalRepository extends JpaRepository<AgentApprovalEntity, String> {
    List<AgentApprovalEntity> findAllByConversationIdOrderByCreatedAtDesc(String conversationId);
}
