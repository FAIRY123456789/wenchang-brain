package cn.wenchang.brain.runtime;

import cn.wenchang.brain.model.ModelConnectionTestResult;
import cn.wenchang.brain.model.RuntimeModelRequest;
import cn.wenchang.brain.model.RuntimeModelStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** 原子持有当前 ChatClient；切换模型不影响本地 Embedding、VectorStore 和正在执行的请求。 */
@Service
public class RuntimeChatModelProvider {

    private static final Logger log = LoggerFactory.getLogger(RuntimeChatModelProvider.class);

    private static final String SYSTEM_PROMPT = """
            你是“文昌智脑”，面向文昌科普、研学和现场展示。
            优先依据检索到的文昌知识资料回答；综合表达时同时利用当前对话上下文。
            遇到最新、近期、今天、天气、开放状态、交通、政策或航天发射等时效问题，
            必须使用 webSearch 工具或采用系统已经取得的联网搜索结果。
            回答使用简洁、准确、适合公众阅读的中文，不编造事实、数据或来源。
            只输出最终答案，不输出内部推理过程或 reasoning_content。
            """;

    private final ChatModel developmentStubModel;
    private final ChatMemory chatMemory;
    private final RuntimeRemoteModelFactory remoteModelFactory;
    private final RuntimeModelConfigService configService;
    private final AtomicReference<ModelHandle> current;

    public RuntimeChatModelProvider(@Qualifier("developmentStubChatModel") ChatModel developmentStubModel,
                                    ChatMemory chatMemory,
                                    RuntimeRemoteModelFactory remoteModelFactory,
                                    RuntimeModelConfigService configService) {
        this.developmentStubModel = developmentStubModel;
        this.chatMemory = chatMemory;
        this.remoteModelFactory = remoteModelFactory;
        this.configService = configService;
        this.current = new AtomicReference<>(defaultOrUnconfiguredHandle());
    }

    public ModelHandle current() { return current.get(); }
    public RuntimeModelStatus settingsStatus() { return configService.status(current.get().descriptor().mode()); }

    public synchronized RuntimeModelStatus configure(RuntimeModelRequest request) {
        RuntimeModelSettings settings = configService.prepare(request);
        ChatModel remoteModel = remoteModelFactory.create(settings);
        ModelDescriptor descriptor = new ModelDescriptor("REMOTE_RUNTIME", settings.provider(), settings.model(),
                settings.thinkingEnabled());
        current.set(new ModelHandle(buildClient(remoteModel), descriptor));
        configService.commit(settings);
        return settingsStatus();
    }

    public synchronized RuntimeModelStatus clear() {
        return restoreDefault();
    }

    public synchronized RuntimeModelStatus restoreDefault() {
        configService.clearRuntime();
        current.set(defaultOrUnconfiguredHandle());
        return settingsStatus();
    }

    public ModelConnectionTestResult testConnection(RuntimeModelRequest request) {
        RuntimeModelSettings settings = null;
        try {
            settings = configService.prepare(request);
            ChatModel model = remoteModelFactory.create(settings);
            String response = ChatClient.builder(model).build().prompt()
                    .system("这是连接测试。不要输出推理过程。")
                    .user("只回复 OK")
                    .call().content();
            if (response == null || response.isBlank()) throw new IllegalStateException("模型返回空内容");
            return new ModelConnectionTestResult(true, settings.provider(), settings.model(),
                    "连接成功", "");
        } catch (Exception exception) {
            String key = settings == null ? request.apiKey() : settings.apiKey();
            String message = configService.redact(deepestMessage(exception), key);
            return new ModelConnectionTestResult(false,
                    request.provider().toLowerCase(Locale.ROOT), request.model(), message, classify(message));
        }
    }

    private ModelHandle unconfiguredHandle() {
        return new ModelHandle(buildClient(developmentStubModel),
                new ModelDescriptor("UNCONFIGURED", "deepseek", "deepseek-chat", false));
    }

    private ModelHandle defaultOrUnconfiguredHandle() {
        RuntimeModelSettings settings = configService.serverDefault();
        if (settings == null) return unconfiguredHandle();
        try {
            ChatModel model = remoteModelFactory.create(settings);
            return new ModelHandle(buildClient(model), new ModelDescriptor("REMOTE_DEFAULT", settings.provider(),
                    settings.model(), settings.thinkingEnabled()));
        } catch (Exception exception) {
            log.warn("服务端模型初始化失败，当前标记为模型未配置：{}",
                    configService.redact(deepestMessage(exception), settings.apiKey()));
            return unconfiguredHandle();
        }
    }

    private ChatClient buildClient(ChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    private String deepestMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return (message == null || message.isBlank()) ? throwable.getClass().getSimpleName() : message;
    }

    private String classify(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (String code : new String[]{"401", "403", "404", "429"}) if (lower.contains(code)) return code;
        if (lower.contains("timeout") || lower.contains("timed out")) return "timeout";
        if (lower.contains("model") && lower.contains("not found")) return "model_not_found";
        if (lower.contains("connect") || lower.contains("network") || lower.contains("unknown host")) return "network_error";
        return "request_error";
    }

    public record ModelHandle(ChatClient chatClient, ModelDescriptor descriptor) { }
    public record ModelDescriptor(String mode, String provider, String model, boolean thinkingEnabled) {
        public String label() {
            if ("UNCONFIGURED".equals(mode)) return "模型未配置";
            return ("deepseek".equals(provider) ? "DeepSeek" : "OpenAI-Compatible") + " · " + model;
        }
    }
}
