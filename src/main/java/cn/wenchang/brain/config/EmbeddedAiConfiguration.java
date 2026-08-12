package cn.wenchang.brain.config;

import cn.wenchang.brain.local.DevelopmentStubChatModel;
import cn.wenchang.brain.local.LocalFeatureHashEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 提供确定性的嵌入模型与仅供自动化测试的未配置占位 ChatModel。 */
@Configuration
public class EmbeddedAiConfiguration {

    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "none", matchIfMissing = true)
    EmbeddingModel localEmbeddingModel() { return new LocalFeatureHashEmbeddingModel(384); }

    @Bean
    ChatModel developmentStubChatModel() { return new DevelopmentStubChatModel(); }
}
