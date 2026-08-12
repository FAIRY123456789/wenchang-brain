package cn.wenchang.brain.runtime;

import java.time.Instant;

/**
 * 仅存在于当前 JVM 进程内的模型设置。这里不用 record，避免默认 toString() 把 API Key 打进日志。
 */
public final class RuntimeModelSettings {

    private final String provider;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final boolean thinkingEnabled;
    private final Instant configuredAt;

    public RuntimeModelSettings(String provider, String baseUrl, String apiKey, String model,
                                boolean thinkingEnabled, Instant configuredAt) {
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
        this.configuredAt = configuredAt;
    }

    public String provider() { return provider; }
    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String model() { return model; }
    public boolean thinkingEnabled() { return thinkingEnabled; }
    public Instant configuredAt() { return configuredAt; }
}
