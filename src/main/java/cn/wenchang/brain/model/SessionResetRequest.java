package cn.wenchang.brain.model;

import jakarta.validation.constraints.NotBlank;

public record SessionResetRequest(@NotBlank String sessionId) { }
