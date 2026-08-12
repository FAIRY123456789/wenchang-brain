package cn.wenchang.brain.runtime;

import cn.wenchang.brain.config.DefaultModelProperties;
import cn.wenchang.brain.model.RuntimeModelRequest;
import cn.wenchang.brain.model.RuntimeModelStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** 管理服务端默认值与进程内覆盖；所有公开视图都只返回脱敏密钥。 */
@Service
public class RuntimeModelConfigService {

    private final RuntimeModelSettings serverDefault;
    private final DefaultModelProperties properties;
    private final AtomicReference<RuntimeModelSettings> runtimeOverride = new AtomicReference<>();

    public RuntimeModelConfigService(DefaultModelProperties properties) {
        this.properties = properties;
        this.serverDefault = buildServerDefault(properties);
    }

    public RuntimeModelSettings prepare(RuntimeModelRequest request) {
        String provider = normalizeProvider(request.provider());
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String model = request.model().trim();
        RuntimeModelSettings existing = effective();
        String key = request.apiKey() == null ? "" : request.apiKey().trim();
        if (key.isBlank() && existing != null) key = existing.apiKey();
        if (key.isBlank()) throw new IllegalArgumentException("API Key 不能为空");
        return new RuntimeModelSettings(provider, baseUrl, key, model,
                request.thinkingEnabled(), Instant.now());
    }

    public void commit(RuntimeModelSettings settings) { runtimeOverride.set(settings); }
    public void clearRuntime() { runtimeOverride.set(null); }
    public RuntimeModelSettings runtimeOverride() { return runtimeOverride.get(); }
    public RuntimeModelSettings serverDefault() { return serverDefault; }
    public RuntimeModelSettings effective() {
        RuntimeModelSettings override = runtimeOverride.get();
        return override == null ? serverDefault : override;
    }

    public RuntimeModelStatus status(String modelMode) {
        RuntimeModelSettings settings = effective();
        if (settings == null) {
            return new RuntimeModelStatus(properties.getProvider(), properties.getBaseUrl(), properties.getModel(),
                    properties.isThinkingEnabled(), false, false, null, "UNCONFIGURED", false, null);
        }
        boolean runtime = runtimeOverride.get() != null;
        return new RuntimeModelStatus(settings.provider(), settings.baseUrl(), settings.model(),
                settings.thinkingEnabled(), true, true, mask(settings.apiKey()), modelMode, runtime,
                settings.configuredAt());
    }

    public String redact(String message, String requestKey) {
        String safe = message == null || message.isBlank() ? "未知连接错误" : message;
        if (requestKey != null && !requestKey.isBlank()) safe = safe.replace(requestKey, "***");
        RuntimeModelSettings effective = effective();
        if (effective != null) safe = safe.replace(effective.apiKey(), "***");
        safe = safe.replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
        return safe.length() <= 700 ? safe : safe.substring(0, 700) + "…";
    }

    public String mask(String key) {
        if (key == null || key.isBlank()) return null;
        int length = key.length();
        if (length <= 6) return key.substring(0, 1) + "••••" + key.substring(length - 1);
        int prefix = Math.min(5, length - 4);
        return key.substring(0, prefix) + "••••••••" + key.substring(length - 4);
    }

    private RuntimeModelSettings buildServerDefault(DefaultModelProperties source) {
        String key = source.getApiKey() == null ? "" : source.getApiKey().trim();
        if (key.isBlank()) return null;
        return new RuntimeModelSettings(normalizeProvider(source.getProvider()), normalizeBaseUrl(source.getBaseUrl()),
                key, source.getModel().trim(), source.isThinkingEnabled(), Instant.now());
    }

    private String normalizeProvider(String provider) {
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("custom") || normalized.equals("openai-compatible")) return "custom";
        if (normalized.equals("deepseek")) return "deepseek";
        throw new IllegalArgumentException("Provider 仅支持 deepseek 或 custom");
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        if (!normalized.matches("https?://.+")) {
            throw new IllegalArgumentException("API Base URL 必须以 http:// 或 https:// 开头");
        }
        return normalized.replaceAll("/+$", "");
    }
}
