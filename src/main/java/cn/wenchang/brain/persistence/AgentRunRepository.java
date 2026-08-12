package cn.wenchang.brain.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, String> {
    List<AgentRunEntity> findAllByConversationIdOrderByStartedAtDesc(String conversationId);
}
