package cn.wenchang.brain;

import cn.wenchang.brain.persistence.ConversationEntity;
import cn.wenchang.brain.persistence.ConversationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:conversation-repo;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.vector-store-file=target/repository-test/vector.json",
        "wenchang.trace-file=target/repository-test/trace.jsonl",
        "wenchang.web-search.enabled=false"
})
class ConversationRepositoryTest {

    @Autowired ConversationRepository repository;

    @AfterEach void clean() { repository.deleteAll(); }

    @Test
    void createsReadsSortsAndRenamesConversation() {
        ConversationEntity older = repository.save(new ConversationEntity("older", "旧对话", "local",
                Instant.now().minusSeconds(60)));
        older.touch();
        ConversationEntity newer = repository.save(new ConversationEntity("newer", "新对话", "local", Instant.now()));
        newer.rename("文昌航天产业发展情况");
        repository.save(newer);
        repository.flush();

        assertThat(repository.findById("newer")).get().extracting(ConversationEntity::getTitle)
                .isEqualTo("文昌航天产业发展情况");
        assertThat(repository.findAllByOwnerIdOrderByUpdatedAtDesc("local"))
                .extracting(ConversationEntity::getId).containsExactly("newer", "older");
    }
}
