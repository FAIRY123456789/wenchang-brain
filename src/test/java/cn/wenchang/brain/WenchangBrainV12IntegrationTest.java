package cn.wenchang.brain;

import cn.wenchang.brain.controller.ConversationController;
import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.ConversationSummary;
import cn.wenchang.brain.model.RenameConversationRequest;
import cn.wenchang.brain.model.RuntimeModelRequest;
import cn.wenchang.brain.model.RuntimeModelStatus;
import cn.wenchang.brain.runtime.RuntimeChatModelProvider;
import cn.wenchang.brain.runtime.RuntimeRemoteModelFactory;
import cn.wenchang.brain.service.ConversationMemoryService;
import cn.wenchang.brain.service.ConversationService;
import cn.wenchang.brain.service.WenchangAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:v12;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.vector-store-file=target/v12-test/vector.json",
        "wenchang.trace-file=target/v12-test/trace.jsonl",
        "wenchang.web-search.enabled=false",
        "wenchang.ai.default.provider=deepseek",
        "wenchang.ai.default.base-url=https://api.deepseek.com",
        "wenchang.ai.default.api-key=test-default-secret-9x2f",
        "wenchang.ai.default.model=deepseek-chat",
        "wenchang.ai.default.thinking-enabled=false"
})
class WenchangBrainV12IntegrationTest {

    @Autowired ConversationController controller;
    @Autowired ConversationService conversations;
    @Autowired ConversationMemoryService memory;
    @Autowired RuntimeChatModelProvider models;
    @Autowired WenchangAgentService agent;
    @MockitoBean RuntimeRemoteModelFactory remoteFactory;

    private RecordingChatModel recordingModel;

    @BeforeEach
    void setUp() {
        conversations.deleteAll();
        recordingModel = new RecordingChatModel();
        when(remoteFactory.create(any())).thenReturn(recordingModel);
        models.restoreDefault();
    }

    @Test
    void test08ConversationLifecycleCreateListDetailRenameDelete() {
        ConversationSummary created = conversations.resolveForChat(null,
                "请介绍一下 文昌航天产业发展情况以及未来方向");
        conversations.appendUser(created.id(), "第一条问题");

        assertThat(controller.list()).extracting(ConversationSummary::id).contains(created.id());
        assertThat(controller.detail(created.id()).messages()).hasSize(1);
        assertThat(created.title()).startsWith("文昌航天产业发展情况");

        ConversationSummary renamed = controller.rename(created.id(), new RenameConversationRequest("航天产业观察"));
        assertThat(renamed.title()).isEqualTo("航天产业观察");
        assertThat(controller.delete(created.id()).getStatusCode().value()).isEqualTo(204);
        assertThat(controller.list()).isEmpty();
    }

    @Test
    void test09RestoresLatestPersistedMessagesIntoSpringAiMemory() {
        ConversationSummary created = conversations.resolveForChat(null, "记忆恢复测试");
        for (int index = 0; index < 10; index++) {
            conversations.appendUser(created.id(), "用户消息" + index);
            conversations.appendAssistant(created.id(), response("助手消息" + index));
        }
        memory.clear(created.id());

        assertThat(memory.restoredMessageCount(created.id())).isEqualTo(18);
    }

    @Test
    void test10PersistedContextParticipatesInFollowUpAfterMemoryRestore() {
        ConversationSummary created = conversations.resolveForChat(null, "文昌有哪些适合研学的地方？");
        conversations.appendUser(created.id(), "文昌有哪些适合研学的地方？");
        conversations.appendAssistant(created.id(), response("可以关注航天、生态与历史文化。"));
        memory.clear(created.id());
        memory.ensureRestored(created.id());

        models.configure(new RuntimeModelRequest("deepseek", "https://api.deepseek.com",
                "test-runtime-context-key", "deepseek-chat", false));
        agent.chat("如果只有一天呢？", created.id());

        assertThat(recordingModel.prompts().get(recordingModel.prompts().size() - 1))
                .contains("文昌有哪些适合研学的地方", "如果只有一天呢");
    }

    @Test
    void test11ServerDefaultSettingsAreEffectiveAndApiKeyIsMasked() throws Exception {
        RuntimeModelStatus status = models.restoreDefault();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(status);

        assertThat(status.modelMode()).isEqualTo("REMOTE_DEFAULT");
        assertThat(status.apiKeyMasked()).isEqualTo("test-••••••••9x2f");
        assertThat(json).doesNotContain("test-default-secret-9x2f");
    }

    @Test
    void test12RuntimeOverrideHasPriorityAndCanRestoreServerDefault() {
        RuntimeModelStatus overridden = models.configure(new RuntimeModelRequest("deepseek",
                "https://runtime.example.com", "test-runtime-secret-abcd", "runtime-model", true));
        assertThat(overridden.modelMode()).isEqualTo("REMOTE_RUNTIME");
        assertThat(overridden.runtimeOverride()).isTrue();
        assertThat(overridden.model()).isEqualTo("runtime-model");

        RuntimeModelStatus restored = models.restoreDefault();
        assertThat(restored.modelMode()).isEqualTo("REMOTE_DEFAULT");
        assertThat(restored.runtimeOverride()).isFalse();
        assertThat(restored.model()).isEqualTo("deepseek-chat");
    }

    private ChatResponseDto response(String answer) {
        return new ChatResponseDto(answer, List.of(), List.of(), "trace", 1,
                "REMOTE_DEFAULT", "deepseek", "deepseek-chat", null);
    }

    private static final class RecordingChatModel implements ChatModel {
        private final List<String> prompts = new CopyOnWriteArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Mock V1.2 回答"))));
        }

        List<String> prompts() { return prompts; }
    }
}
