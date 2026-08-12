package cn.wenchang.brain.search;

import java.time.Instant;

public record SearchProviderHealth(
        String provider,
        String health,
        long latencyMs,
        Instant lastSuccess,
        String lastError,
        String errorType
) {
    public boolean available() { return "AVAILABLE".equals(health); }
}
