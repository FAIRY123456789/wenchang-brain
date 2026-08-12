package cn.wenchang.brain.model;

public record AgentProgress(String stage, String message, Integer count) {
    public static AgentProgress of(String stage, String message) {
        return new AgentProgress(stage, message, null);
    }

    public static AgentProgress found(int count) {
        return new AgentProgress("retrieved", "找到 " + count + " 条相关资料", count);
    }
}
