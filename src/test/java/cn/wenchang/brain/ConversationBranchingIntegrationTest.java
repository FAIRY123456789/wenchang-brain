package cn.wenchang.brain;

import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.MessageRevisionOption;
import cn.wenchang.brain.service.ConversationMemoryService;
import cn.wenchang.brain.service.ConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:conversation-branches;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.ai.default.api-key=",
        "wenchang.vector-store-file=target/conversation-branches/vector.json",
        "wenchang.trace-file=target/conversation-branches/trace.jsonl",
        "wenchang.web-search.enabled=false"
})
class ConversationBranchingIntegrationTest {

    @Autowired ConversationService conversations;
    @Autowired ConversationMemoryService memory;

    @AfterEach
    void clean() {
        conversations.deleteAll();
    }

    @Test
    void editsQuestionIntoSiblingRevisionAndPreservesBothConversationBranches() {
        var conversation = conversations.resolveForChat(null, "原问题", "wenchang");
        Long originalQuestion = conversations.appendUser(conversation.id(), "原问题", "wenchang", null, null);
        conversations.appendAssistant(conversation.id(), response("原回答"), originalQuestion);
        Long followUp = conversations.appendUser(conversation.id(), "原分支追问", "wenchang", null, null);
        conversations.appendAssistant(conversation.id(), response("原分支追问回答"), followUp);

        conversations.prepareEdit(conversation.id(), originalQuestion);
        memory.clear(conversation.id());
        assertThat(memory.restoredMessageCount(conversation.id())).isZero();

        Long revisedQuestion = conversations.appendUser(conversation.id(), "修改后的问题",
                "wenchang", null, originalQuestion);
        conversations.appendAssistant(conversation.id(), response("修改后的回答"), revisedQuestion);

        var revised = conversations.detail(conversation.id());
        assertThat(revised.messages()).extracting(message -> message.content())
                .containsExactly("修改后的问题", "修改后的回答");
        assertThat(revised.messages().get(0).revisions())
                .extracting(MessageRevisionOption::index).containsExactly(1, 2);
        assertThat(revised.messages().get(0).revisions())
                .filteredOn(MessageRevisionOption::active)
                .singleElement().extracting(MessageRevisionOption::messageId).isEqualTo(revisedQuestion);

        var original = conversations.activateRevision(conversation.id(), originalQuestion);
        assertThat(original.messages()).extracting(message -> message.content())
                .containsExactly("原问题", "原回答", "原分支追问", "原分支追问回答");
        memory.clear(conversation.id());
        assertThat(memory.restoredMessageCount(conversation.id())).isEqualTo(4);

        var switchedBack = conversations.activateRevision(conversation.id(), revisedQuestion);
        assertThat(switchedBack.messages()).extracting(message -> message.content())
                .containsExactly("修改后的问题", "修改后的回答");
    }

    @Test
    void editingLaterQuestionRestoresOnlySharedPrefixIntoModelMemory() {
        var conversation = conversations.resolveForChat(null, "第一问", "policy");
        Long first = conversations.appendUser(conversation.id(), "第一问", "policy", "policy-search", null);
        conversations.appendAssistant(conversation.id(), response("第一答"), first);
        Long second = conversations.appendUser(conversation.id(), "第二问旧版", "policy", "policy-search", null);
        conversations.appendAssistant(conversation.id(), response("第二答旧版"), second);

        conversations.prepareEdit(conversation.id(), second);
        memory.clear(conversation.id());
        assertThat(memory.restoredMessageCount(conversation.id())).isEqualTo(2);

        Long revised = conversations.appendUser(conversation.id(), "第二问新版",
                "policy", "policy-search", second);
        conversations.appendAssistant(conversation.id(), response("第二答新版"), revised);

        var detail = conversations.detail(conversation.id());
        assertThat(detail.messages()).extracting(message -> message.content())
                .containsExactly("第一问", "第一答", "第二问新版", "第二答新版");
        assertThat(detail.messages().get(2).agentId()).isEqualTo("policy");
        assertThat(detail.messages().get(2).skillId()).isEqualTo("policy-search");
        assertThat(detail.messages().get(2).revisionCount()).isEqualTo(2);
    }

    private ChatResponseDto response(String answer) {
        return new ChatResponseDto(answer, List.of(), List.of(), "trace", 1,
                "REMOTE_DEFAULT", "deepseek", "deepseek-chat", null);
    }
}