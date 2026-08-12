package cn.wenchang.brain.model;

import java.time.Instant;

/** 安全的设置视图：只说明是否已有 Key，永远不返回原始 Key。 */
public record RuntimeModelStatus(
        String provider,
        String baseUrl,
        String model,
        boolean thinkingEnabled,
        boolean configured,
        boolean apiKeyConfigured,
        String apiKeyMasked,
        String modelMode,
        boolean runtimeOverride,
        Instant configuredAt
) { }
