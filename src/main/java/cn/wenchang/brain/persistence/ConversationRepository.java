package cn.wenchang.brain.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    List<ConversationEntity> findAllByOwnerIdOrderByUpdatedAtDesc(String ownerId);
    void deleteAllByOwnerId(String ownerId);
}
