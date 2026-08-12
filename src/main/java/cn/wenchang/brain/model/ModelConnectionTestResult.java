package cn.wenchang.brain.model;

public record ModelConnectionTestResult(
        boolean success,
        String provider,
        String model,
        String message,
        String errorType
) { }
