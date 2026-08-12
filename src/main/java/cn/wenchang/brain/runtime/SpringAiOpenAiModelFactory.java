package cn.wenchang.brain.runtime;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/** 使用 Spring AI 2.0 的程序化 Builder 创建 OpenAI-compatible ChatModel。 */
@Component
public class SpringAiOpenAiModelFactory implements RuntimeRemoteModelFactory {

    @Override
    public ChatModel create(RuntimeModelSettings settings) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .model(settings.model())
                .timeout(Duration.ofSeconds(90))
                .maxRetries(1);

        if ("deepseek".equals(settings.provider())) {
            // DeepSeek V4 默认开启思考，因此普通现场问答也必须显式发送 disabled。
            options.extraBody(Map.of("thinking", Map.of(
                    "type", settings.thinkingEnabled() ? "enabled" : "disabled")));
            if (settings.thinkingEnabled()) options.reasoningEffort("high");
        } else if (settings.thinkingEnabled()) {
            // 自定义兼容服务不假定 DeepSeek 私有字段，只使用 OpenAI-compatible reasoning_effort。
            options.reasoningEffort("high");
        }
        return OpenAiChatModel.builder().options(options.build()).build();
    }
}
