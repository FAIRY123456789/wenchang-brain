package cn.wenchang.brain.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findAllByConversation_IdOrderByCreatedAtAscIdAsc(String conversationId);
    List<MessageEntity> findAllByConversation_IdOrderByCreatedAtDescIdDesc(String conversationId, Pageable pageable);
    long countByConversation_Id(String conversationId);
}
