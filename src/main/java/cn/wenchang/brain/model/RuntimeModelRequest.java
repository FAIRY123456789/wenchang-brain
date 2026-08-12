package cn.wenchang.brain.model;

import jakarta.validation.constraints.NotBlank;

public record RuntimeModelRequest(
        @NotBlank String provider,
        @NotBlank String baseUrl,
        String apiKey,
        @NotBlank String model,
        boolean thinkingEnabled
) { }
