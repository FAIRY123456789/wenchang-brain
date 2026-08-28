package cn.wenchang.brain.v14;

import cn.wenchang.brain.model.ChatRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestContractTest {

    @Test
    void defaultsAgentAndNormalizesAgentSkillAndConversation() {
        ChatRequest defaults = new ChatRequest("文昌有什么特色？", "session-1", null, null, null);
        assertThat(defaults.effectiveConversationId()).isEqualTo("session-1");
        assertThat(defaults.effectiveAgentId()).isEqualTo("wenchang");
        assertThat(defaults.effectiveSkillId()).isNull();

        ChatRequest selected = new ChatRequest("查询近期航天政策", "legacy-session", "conversation-1",
                "  aerospace  ", "  deep-research  ");
        assertThat(selected.effectiveConversationId()).isEqualTo("conversation-1");
        assertThat(selected.effectiveAgentId()).isEqualTo("aerospace");
        assertThat(selected.effectiveSkillId()).isEqualTo("deep-research");

        ChatRequest edited = new ChatRequest("修改后的问题", null, "conversation-1",
                "policy", "policy-search", 42L);
        assertThat(edited.editMessageId()).isEqualTo(42L);
    }
}
