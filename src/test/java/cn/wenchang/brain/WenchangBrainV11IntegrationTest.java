package cn.wenchang.brain;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.ModelConnectionTestResult;
import cn.wenchang.brain.model.RuntimeModelRequest;
import cn.wenchang.brain.model.RuntimeModelStatus;
import cn.wenchang.brain.runtime.RuntimeChatModelProvider;
import cn.wenchang.brain.runtime.RuntimeRemoteModelFactory;
import cn.wenchang.brain.service.WenchangAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:v11;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wenchang.vector-store-file=target/v11-test-data/wenchang-vector-store.json",
        "wenchang.trace-file=target/v11-test-logs/agent-trace.jsonl",
        "wenchang.web-search.enabled=false"
})
class WenchangBrainV11IntegrationTest {

    private static final String TEST_KEY = "unit-test-runtime-key-never-log";

    @Autowired RuntimeChatModelProvider modelProvider;
    @Autowired WenchangAgentService agentService;
    @Autowired WenchangProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean RuntimeRemoteModelFactory remoteModelFactory;

    private RecordingChatModel recordingModel;

    @BeforeEach
    void setUp() {
        recordingModel = new RecordingChatModel();
        when(remoteModelFactory.create(any())).thenReturn(recordingModel);
        modelProvider.clear();
    }

    @AfterEach
    void tearDown() { modelProvider.clear(); }

    @Test
    void test04RuntimeModelConfigurationNeverReturnsOrTracesKey() throws Exception {
        RuntimeModelStatus status = modelProvider.configure(request(false));
        assertThat(status.configured()).isTrue();
        assertThat(status.apiKeyConfigured()).isTrue();
        String getBody = objectMapper.writeValueAsString(modelProvider.settingsStatus());
        assertThat(getBody).doesNotContain(TEST_KEY, "\"apiKey\":");

        ModelConnectionTestResult testResult = modelProvider.testConnection(request(false));
        assertThat(testResult.success()).isTrue();
        assertThat(testResult.message()).isEqualTo("连接成功");
        assertThat(objectMapper.writeValueAsString(testResult)).doesNotContain(TEST_KEY);

        agentService.chat("介绍文昌的生态特点", "v11-key-trace-session");
        String trace = Files.readString(Path.of(properties.getTraceFile()), StandardCharsets.UTF_8);
        assertThat(trace).doesNotContain(TEST_KEY);
    }

    @Test
    void test05ShortTermMemoryKeepsThreeTurnMeaningAndCanReset() {
        modelProvider.configure(request(false));
        String session = "v11-memory-session";
        agentService.chat("文昌有哪些适合研学的地方？", session);
        agentService.chat("如果只有一天呢？", session);
        agentService.chat("那如果更偏生态呢？", session);

        String thirdPrompt = recordingModel.prompts().get(recordingModel.prompts().size() - 1);
        assertThat(thirdPrompt).contains("文昌有哪些适合研学的地方", "如果只有一天呢", "那如果更偏生态呢");

        agentService.resetSession(session);
        recordingModel.clear();
        agentService.chat("重新开始", session);
        assertThat(recordingModel.prompts().get(0)).doesNotContain("如果只有一天呢");
    }

    @Test
    void test06RemoteRagUsesRuntimeModelAndReturnsSources() {
        modelProvider.configure(request(true));
        ChatResponseDto response = agentService.chat("请介绍文昌的自然生态特点。", "v11-remote-rag");

        assertThat(response.modelMode()).isEqualTo("REMOTE_RUNTIME");
        assertThat(response.modelProvider()).isEqualTo("deepseek");
        assertThat(response.modelName()).isEqualTo("deepseek-v4-flash");
        assertThat(response.sources()).isNotEmpty();
        assertThat(recordingModel.prompts()).isNotEmpty();
    }

    @Test
    void test07RemoteAgentExecutesTemporalRouterWebToolSourceAndTrace() throws Exception {
        modelProvider.configure(request(false));
        ChatResponseDto response = agentService.chat("文昌近期一次重要航天发射是什么？", "v11-remote-agent");

        assertThat(response.modelMode()).isEqualTo("REMOTE_RUNTIME");
        assertThat(response.toolsUsed()).contains("webSearch");
        assertThat(response.sources()).isNotEmpty();
        assertThat(recordingModel.prompts()).isNotEmpty();
        String trace = Files.readString(Path.of(properties.getTraceFile()), StandardCharsets.UTF_8);
        assertThat(trace).contains(response.traceId(), "webSearch", "\"modelMode\":\"REMOTE_RUNTIME\"")
                .doesNotContain(TEST_KEY);
    }

    @Test
    void streamingPathConsumesChatClientFluxAndReturnsMetadata() {
        modelProvider.configure(request(false));
        List<String> chunks = new CopyOnWriteArrayList<>();
        ChatResponseDto response = agentService.stream("文昌的航天特色是什么？", "v11-stream",
                progress -> { }, chunks::add);
        assertThat(chunks).isNotEmpty();
        assertThat(String.join("", chunks)).isEqualTo(response.answer());
        assertThat(response.sources()).isNotEmpty();
    }

    private RuntimeModelRequest request(boolean thinking) {
        return new RuntimeModelRequest("deepseek", "https://api.deepseek.com", TEST_KEY,
                "deepseek-v4-flash", thinking);
    }

    private static final class RecordingChatModel implements ChatModel {
        private final List<String> prompts = new CopyOnWriteArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Mock Remote 最终回答"))));
        }

        List<String> prompts() { return prompts; }
        void clear() { prompts.clear(); }
    }
}
