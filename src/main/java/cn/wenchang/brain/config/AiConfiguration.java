package cn.wenchang.brain.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {

    /**
     * Spring AI 的 MessageWindowChatMemory 只保留最近若干条消息；repository 负责实际保存。
     * 第一版使用进程内仓库，重启后清空，避免为小型展示应用引入数据库。
     */
    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(18)
                .build();
    }

    /** SSE 请求在独立工作线程执行阻塞式检索和 Flux 消费，避免占住 Servlet 请求线程。 */
    @Bean
    TaskExecutor agentStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("wenchang-stream-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(64);
        executor.initialize();
        return executor;
    }
}
