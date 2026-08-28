package cn.wenchang.brain.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record SearchProviderHealth(
        String provider,
        String health,
        long latencyMs,
        Instant lastSuccess,
        String lastError,
        String errorType
) {
    @JsonProperty("available")
    public boolean available() { return "AVAILABLE".equals(health); }
}
