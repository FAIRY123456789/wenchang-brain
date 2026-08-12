package cn.wenchang.brain;

import cn.wenchang.brain.persistence.ConversationEntity;
import cn.wenchang.brain.persistence.ConversationRepository;
import cn.wenchang.brain.persistence.MessageEntity;
import cn.wenchang.brain.persistence.MessageRepository;
import cn.wenchang.brain.persistence.MessageRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:message-repo;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.vector-store-file=target/message-repository-test/vector.json",
        "wenchang.trace-file=target/message-repository-test/trace.jsonl",
        "wenchang.web-search.enabled=false"
})
class MessageRepositoryTest {

    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;

    @AfterEach void clean() { conversations.deleteAll(); }

    @Test
    void writesReadsInOrderAndDeletesMessagesByCascade() {
        conversations.saveAndFlush(new ConversationEntity("cascade", "测试", "local", Instant.now()));
        ConversationEntity conversation = conversations.getReferenceById("cascade");
        messages.save(new MessageEntity(conversation, MessageRole.USER, "第一条", Instant.now().minusSeconds(1)));
        messages.save(new MessageEntity(conversation, MessageRole.ASSISTANT, "第二条", Instant.now()));
        messages.flush();

        assertThat(messages.findAllByConversation_IdOrderByCreatedAtAscIdAsc("cascade"))
                .extracting(MessageEntity::getContent).containsExactly("第一条", "第二条");

        conversations.delete(conversation);
        conversations.flush();
        assertThat(messages.findAllByConversation_IdOrderByCreatedAtAscIdAsc("cascade")).isEmpty();
    }
}
