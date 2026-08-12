package cn.wenchang.brain.runtime;

import org.springframework.ai.chat.model.ChatModel;

/** 独立工厂让运行时模型构造可以用 Mock ChatModel 做结构与 Harness 测试。 */
public interface RuntimeRemoteModelFactory {
    ChatModel create(RuntimeModelSettings settings);
}
